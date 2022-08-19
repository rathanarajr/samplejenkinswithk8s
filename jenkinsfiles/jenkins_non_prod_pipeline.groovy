
def sendEmail()
{
    mailRecipients = "snallabotul2@dxc.com,deepika.c3@dxc.com,priyanka.a@dxc.com"
    emailext body: '''Hello Team, We got the result for the Sample App, please find the logs in the attachment''',
    mimeType: 'text/html',
    attachLog: true,
    subject: "${currentBuild.fullDisplayName} - Build ${currentBuild.result}",
    to: "${mailRecipients}",
    replyTo: "${mailRecipients}",
    recipientProviders: [[$class: 'CulpritsRecipientProvider']]
}

pipeline {
  agent {label 'master'}
  
  environment {
      APPLICATION_NAME="sample-image";
      K8s_DEPLOYMENT_FILE="sample-deployment.yaml";
      K8s_DEPLOYMENT_SERVICE_FILE="sample-service.yaml";
      BASE_IMAGE_NAME="${DOCKER_REPO_PATH}/${APPLICATION_NAME}";
      DOCKER_IMAGE="${BASE_IMAGE_NAME}:${GIT_COMMIT}";
      LATEST_TAG="${BASE_IMAGE_NAME}:latest";
      DEV_KUBE_CONFIG_PATH = "/home/appadmin/.kube/dev_config";
      QA_KUBE_CONFIG_PATH = "/home/appadmin/.kube/qa_config";
      TSD_KUBE_CONFIG_PATH = "/home/appadmin/.kube/mat_config";
      TSA_KUBE_CONFIG_PATH = "/home/appadmin/.kube/nft_config";                        

  }
  stages {
    stage ('Git Checkout') {
      when {
           expression { params.deploy_environment == 'dev'}
      }
      steps {
         dir("${WORKSPACE}"){
         git (url: 'https://github.dxc.com/LMA-FERN-App-Remediation/sample.git',
         branch: 'master',
         credentialsId: '812b3061-c4f2-4cf6-8c0a-26c8f843ee1d')
         }
      }
    }

    stage('Unit Test - Junit') {
      when {
          expression { params.deploy_environment == 'dev'}
      }
      steps {
        
        sh 'mvn clean test surefire-report:report'
      }

     // post {
      //    success {
        //     junit '**/target/surefire-reports/*.xml'
       //   }
    //  }

    }
    stage('Sonar Analysis') {
       when {
           expression { params.deploy_environment == 'dev'}
       }
       steps {
         dir("${WORKSPACE}"){
         script {
	          stage ('Static Code Analysis') {
                 withSonarQubeEnv('SonarQube') {
	    	         def sonarscanner_home = tool 'SonarScanner';
	    	         def SONARQUBE_URL = 'http://10.16.26.51:9000/';
                 sh "${sonarscanner_home}/bin/sonar-scanner -X -Dsonar.host.url=${SONARQUBE_URL} -Dsonar.projectKey=Sample -Dsonar.login=admin -Dsonar.password=admin -Dsonar.projectVersion=1.0 -Dsonar.sources=src/ -Dsonar.sourceEncoding=UTF-8" 

                } 
	          }
            stage('Quality Check') {
                 sleep(60);
                 timeout(time: 1, unit: 'MINUTES') { // If something goes wrong pipeline will be killed after a timeout
                 def qg = waitForQualityGate();
                  if (qg.status != "OK") {
                 currentBuild.result='FAILURE';
                 error "Pipeline aborted due to quality gate coverage failure: ${qg.status}"
                 }
                 else{
                 echo "Quality gate passed: ${qg.status}" 
                 }
                 }

            }
            stage('Sonar Report Generation') {
               sh label: '', script: '''#!/bin/bash
                     CURRENTEPOCTIME=`date +"%Y-%m-%d"`
                     rm -rf sample-DEV-sonar_reports*
                     mkdir -p sample-DEV-sonar_reports_${BUILD_ID}
                     java -jar /data/lib/jenkins/sonar_lib/sonar-cnes-report-3.1.0.jar -p sample -s http://10.16.26.51:9000/ -m -f -o CAS-SVC-DEV-sonar_reports_${BUILD_ID}/
                     tar -czvf sample-DEV-sonar_reports_${BUILD_ID}_${CURRENTEPOCTIME}.tgz sample-DEV-sonar_reports_${BUILD_ID}'''
            }
            stage('email sonar report') {
                        env.ForEmailPlugin = env.WORKSPACE                    
                        emailext mimeType: 'text/html',
			                  attachLog: true,
		                    attachmentsPattern: '**/*.tgz',
                        body:'''Hello Team, Code scan and code quality check for the Sample App is done. Please find the complete report in the email attachments.''',
                        subject: currentBuild.currentResult + " : " + env.JOB_NAME,
                        to:'snallabotul2@dxc.com,deepika.c3@dxc.com,priyanka.a@dxc.com'
            }

         }
         }
      }
    } 

    stage('Building Sample App') {
      when {
           expression { params.deploy_environment == 'dev'}
      }
      steps {
        sh 'mvn clean package'

      }

    }

    stage('Docker Build and Publish Image') {
      when {
           expression { params.deploy_environment == 'dev'}
      }
      steps {
        sh '''#cd $WORKSPACE           
              sh dockerbuild.sh
'''
      }

    }
   
    stage('Deployment into Dev k8s cluster') {
      when {
           expression { params.deploy_environment == 'dev'}
      }
      steps {
             sh ''' cd $WORKSPACE
                    cd k8s
                    export ENV_VAR=dev
                    sed -i -e "s@$BASE_IMAGE_NAME@"$DOCKER_IMAGE"@g" ${K8s_DEPLOYMENT_FILE} -e "s@ENV@"$ENV_VAR"@g"

                    kubectl --kubeconfig ${DEV_KUBE_CONFIG_PATH} apply -f . '''          
      }
    }

    stage ('Deployment Promotion to Higher Environment cluster') {
       when {
           expression { params.promotion_environment ==~ /qa|tsd|tsa/ }
       }
       steps {
         script {
           stage ('Git Checkout') {
                dir("${WORKSPACE}"){
                git (url: 'https://github.dxc.com/LMA-FERN-App-Remediation/sample.git',
                branch: 'master',
                credentialsId: '812b3061-c4f2-4cf6-8c0a-26c8f843ee1d')
                }

           }
           stage ('Promotion Environmnts') {
               script {      
                  if (params.promotion_environment=='qa'){   
                     sh ''' cd $WORKSPACE
                      cd k8s
                      export ENV_VAR=dev

                      sed -i -e "s@$BASE_IMAGE_NAME@"$DOCKER_IMAGE"@g" ${K8s_DEPLOYMENT_FILE} -e "s@ENV@"$ENV_VAR"@g"

                      kubectl --kubeconfig ${QA_KUBE_CONFIG_PATH} apply -f .
                     
                      kubectl --kubeconfig ${QA_KUBE_CONFIG_PATH} get nodes '''
                  }
                  else if (params.promotion_environment=='tsd'){ 
                     sh ''' cd $WORKSPACE
                       cd k8s
                       kubectl --kubeconfig ${TSD_KUBE_CONFIG_PATH} get nodes

                       #kubectl --kubeconfig ${TSD_KUBE_CONFIG_PATH} apply -f . '''
                  }
                  else if (params.promotion_environment=='tsa'){
                    sh ''' cd $WORKSPACE
                       cd k8s
                       kubectl --kubeconfig ${TSA_KUBE_CONFIG_PATH} get nodes

                       #kubectl --kubeconfig ${TSA_KUBE_CONFIG_PATH} apply -f . '''
                  }
               }
             
           }

         }
       }
    }
       
 
  }
post {
    success {
        sendEmail();
    }
      
    failure {
        sendEmail();
    }
}

}


