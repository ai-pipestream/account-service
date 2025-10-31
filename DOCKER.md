# Docker Build and Push Guide

## Prerequisites
- Docker installed and running
- Access to `git.rokkon.com` container registry

## Build Docker Image

The service uses Quarkus `container-image-docker` extension to build Docker images using the generated Dockerfile.

### Build Image Locally
```bash
./gradlew build -Dquarkus.container-image.build=true
```

This will:
1. Compile the application
2. Create the `quarkus-app/` build output
3. Build a Docker image using `src/main/docker/Dockerfile.jvm`
4. Tag the image as:
   - `git.rokkon.com/io-pipeline/account-service:latest`
   - `git.rokkon.com/io-pipeline/account-service:1.0.0`

### Build and Push to Registry
```bash
./gradlew build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
```

## Configuration

Container image settings are in `src/main/resources/application.properties`:

```properties
quarkus.container-image.registry=git.rokkon.com
quarkus.container-image.group=io-pipeline
quarkus.container-image.name=platform-registration-service
quarkus.container-image.tag=latest
quarkus.container-image.additional-tags=${quarkus.application.version}
```

## Available Dockerfiles

Quarkus provides multiple Dockerfiles in `src/main/docker/`:

- **Dockerfile.jvm** - Standard JVM mode (default, used by build)
- **Dockerfile.legacy-jar** - Uber-jar mode
- **Dockerfile.native** - Native compilation (requires GraalVM)
- **Dockerfile.native-micro** - Native with minimal base image

## Running the Container

```bash
# Pull from registry
docker pull git.rokkon.com/io-pipeline/account-service:latest

# Run locally
docker run -p 38105:8080 \
  -e PIPELINE_CONSUL_HOST=consul \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql:3306/pipeline \
  git.rokkon.com/io-pipeline/account-service:latest
```

## Verify Image

```bash
# List images
docker images | grep account-service

# Inspect image
docker inspect git.rokkon.com/io-pipeline/account-service:latest
```

## CI/CD Integration

In CI/CD pipelines, use:

```bash
./gradlew clean build \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.username=$REGISTRY_USER \
  -Dquarkus.container-image.password=$REGISTRY_PASSWORD
```

