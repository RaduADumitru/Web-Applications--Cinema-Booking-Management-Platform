FROM maven:3.9.16-eclipse-temurin-21

LABEL authors="Radu"
    
WORKDIR /workspace/cinema

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x ./mvnw \
	&& apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

EXPOSE 8080

CMD ["sh", "-c", "./mvnw spring-boot:run"]