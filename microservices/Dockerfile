FROM maven:3.9.16-eclipse-temurin-21

LABEL authors="Radu"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY . .

# Build & install all modules (common + the 4 services + gateway) into the image's local
# Maven repository so each container can run its module offline, isolated from the others.
RUN mvn -q -Dmaven.test.skip=true install

EXPOSE 8080
