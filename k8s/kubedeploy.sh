
#kubectl --kubeconfig ${DEV_KUBE_CONFIG_PATH} delete -f ${K8s_DEPLOYMENT_FILE}
#kubectl --kubeconfig ${DEV_KUBE_CONFIG_PATH} delete -f ${K8s_DEPLOYMENT_SERVICE_FILE}

sed -i -e "s@$BASE_IMAGE_NAME@"$DOCKER_IMAGE"@g" ${K8s_DEPLOYMENT_FILE} -e "s@ENV@"$ENV_VAR"@g"

kubectl --kubeconfig ${DEV_KUBE_CONFIG_PATH} apply -f ${K8s_DEPLOYMENT_FILE}
kubectl --kubeconfig ${DEV_KUBE_CONFIG_PATH} apply -f ${K8s_DEPLOYMENT_SERVICE_FILE}
