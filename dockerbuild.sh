docker login ${DOCKER_REGISTRY_URL} -u  ${DOCKER_USER} -p ${DOCKER_PASSWORD}

docker build -t ${DOCKER_IMAGE} .
docker build -t ${LATEST_TAG} .

docker push ${DOCKER_IMAGE}
docker push ${LATEST_TAG}

docker image remove ${DOCKER_IMAGE}
docker image remove ${LATEST_TAG}
