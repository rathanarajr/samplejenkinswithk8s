FROM java

# expect a build-time variable
ARG A_VARIABLE=dev
# use the value to set the ENV var default
ENV an_env_var=$A_VARIABLE
#ENV ENVIRONMENT=dev

ADD ./target/myproject-0.0.1-SNAPSHOT.jar /myproject-0.0.1-SNAPSHOT.jar
RUN mkdir -p /opt/lma/envConfig
COPY /envConfig/ /opt/lma/envConfig/
ADD ./run.sh /run.sh
RUN chmod a+x /run.sh
EXPOSE 8080:8080
CMD /run.sh
