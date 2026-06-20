# Spring Cloud Config Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Spring Cloud Config server that centralizes all microservice configuration, serves `{cipher}`-encrypted secrets, and lets config be refreshed live across the fleet via Spring Cloud Bus.

**Architecture:** A new `config-server` module (port 8888, native filesystem backend, config files mounted as a Docker volume) serves config to every other service. Services contact it by URL at startup (`spring.config.import=configserver:`), then register with Eureka. Secrets (`JWT_SECRET_KEY`, `TMDB_API_KEY`) are stored as `{cipher}` ciphertext and decrypted server-side using a symmetric `ENCRYPT_KEY`. Dynamic refresh uses Spring Cloud Bus over RabbitMQ — `POST /actuator/busrefresh` to the gateway broadcasts to all instances.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2, Java 21, Maven multi-module reactor, RabbitMQ (`rabbitmq:3-management`), Docker Compose.

---

## Notes & deviations from the spec (read first)

These are deliberate, low-risk refinements discovered while mapping files:

1. **Config server is NOT a bus participant.** The spec mentioned adding the bus to the config server "so it can originate the broadcast." We trigger `busrefresh` from the **gateway** instead, and the config server doesn't need to refresh itself (it re-reads files per request). Keeping the bus off the config server lets it stay a true dependency root with **no `depends_on` on RabbitMQ**. So `spring-cloud-starter-bus-amqp` goes on user/catalog/booking/gateway only.

2. **`app.cache.*` config stays local** in `catalog-service`/`booking-service` `src/main/resources/application.yml`. It is loaded during tests (the test classpath has no `application.yml` to shadow it), so moving it to the config server would break those services' tests. It is static, non-sensitive, non-refreshed config, so local is fine.

3. **Infra connection coordinates use `${ENV}` placeholders inside the served files.** Verified against the Spring Cloud Config docs: the JSON Environment endpoint (used by `spring.config.import=configserver:`) passes `${...}` through literally and the *client* resolves them from its own container env (`resolvePlaceholders` defaults to `false`). `{cipher}` values, by contrast, are always decrypted server-side. So `spring.datasource.password: ${DATABASE_PASSWORD}` in a served file resolves on the client.

4. **`server.port` in served files matches the original local-dev ports** (8081/8082/8083). In Docker the `SERVER_PORT=8080` env var still overrides them (OS env outranks config-data), preserving today's behavior.

---

## File structure

**Created:**
- `microservices/config-server/pom.xml` — module POM (`spring-cloud-config-server`)
- `microservices/config-server/src/main/java/com/awbd/cinema/ConfigServerApplication.java` — `@EnableConfigServer`
- `microservices/config-server/src/main/resources/application.yml` — server config (port, native backend)
- `microservices/config-server/src/test/resources/test-config/application.yml` — fixture config dir for tests
- `microservices/config-server/src/test/resources/test-config/demo-app.yml` — fixture with a placeholder, for the pass-through test
- `microservices/config-server/src/test/java/com/awbd/cinema/ConfigServerApplicationTest.java` — context-load test
- `microservices/config-server/src/test/java/com/awbd/cinema/EncryptionEndpointTest.java` — `/encrypt`+`/decrypt` round-trip + serve/placeholder test
- `microservices/config-server/config/application.yml` — **shared** runtime config (served to all clients)
- `microservices/config-server/config/user-service.yml`
- `microservices/config-server/config/catalog-service.yml`
- `microservices/config-server/config/booking-service.yml`
- `microservices/config-server/config/gateway.yml`
- `microservices/gateway/src/test/resources/application.yml` — test override (no config import / bus)

**Modified:**
- `microservices/pom.xml` — register `config-server` module
- `microservices/user-service/pom.xml`, `.../catalog-service/pom.xml`, `.../booking-service/pom.xml`, `.../gateway/pom.xml` — add config client + bus + retry deps
- `microservices/user-service/src/main/resources/application.properties` — trim to bootstrap
- `microservices/catalog-service/src/main/resources/application.properties` — trim to bootstrap
- `microservices/booking-service/src/main/resources/application.properties` — trim to bootstrap
- `microservices/gateway/src/main/resources/application.yml` — trim to bootstrap
- `microservices/{user,catalog,booking}-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java` — CSRF-exempt `/actuator/**`
- `microservices/{user,catalog,booking}-service/src/test/resources/application.properties` — disable config client + bus in tests
- `docker-compose.microservices.yml` — add `config-server` + `rabbitmq`, wire clients
- `.env`, `.env.example` — add `ENCRYPT_KEY`, remove `JWT_SECRET_KEY`/`TMDB_API_KEY`
- `README.md` — document config server, encryption workflow, refresh demo, default owner login

---

## Task 1: Create the config-server module

**Files:**
- Create: `microservices/config-server/pom.xml`
- Create: `microservices/config-server/src/main/java/com/awbd/cinema/ConfigServerApplication.java`
- Create: `microservices/config-server/src/main/resources/application.yml`
- Create: `microservices/config-server/src/test/resources/test-config/application.yml`
- Create: `microservices/config-server/src/test/java/com/awbd/cinema/ConfigServerApplicationTest.java`
- Modify: `microservices/pom.xml` (add module)

- [ ] **Step 1: Register the module in the reactor POM**

In `microservices/pom.xml`, add `config-server` as the first module:

```xml
    <modules>
        <module>config-server</module>
        <module>discovery-server</module>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
        <module>booking-service</module>
        <module>gateway</module>
    </modules>
```

- [ ] **Step 2: Create the module POM**

Create `microservices/config-server/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.awbd</groupId>
        <artifactId>microservices</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>..</relativePath>
    </parent>

    <artifactId>config-server</artifactId>
    <packaging>jar</packaging>
    <name>config-server</name>
    <description>Spring Cloud Config server (native backend, symmetric encryption)</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create the application class**

Create `microservices/config-server/src/main/java/com/awbd/cinema/ConfigServerApplication.java`:

```java
package com.awbd.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

- [ ] **Step 4: Create the server's own application.yml**

Create `microservices/config-server/src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:8888}

spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: ${CONFIG_SEARCH_LOCATIONS:file:/config}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Note: the symmetric key is read from the `ENCRYPT_KEY` environment variable (Spring maps it to `encrypt.key` automatically); it is intentionally NOT placed in this file.

- [ ] **Step 5: Create a test fixture config directory**

Create `microservices/config-server/src/test/resources/test-config/application.yml`:

```yaml
sample:
  message: hello-from-config-server
```

- [ ] **Step 6: Write the context-load test**

Create `microservices/config-server/src/test/java/com/awbd/cinema/ConfigServerApplicationTest.java`:

```java
package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=0",
        "spring.profiles.active=native",
        "spring.cloud.config.server.native.search-locations=classpath:/test-config"
})
class ConfigServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -o -pl config-server -am test`
Expected: BUILD SUCCESS; `ConfigServerApplicationTest` passes (context loads with the native profile).

- [ ] **Step 8: Commit**

```bash
git add microservices/pom.xml microservices/config-server
git commit -m "feat(config-server): scaffold Spring Cloud Config server module"
```

---

## Task 2: Encryption + native-serve tests

**Files:**
- Create: `microservices/config-server/src/test/resources/test-config/demo-app.yml`
- Create: `microservices/config-server/src/test/java/com/awbd/cinema/EncryptionEndpointTest.java`

- [ ] **Step 1: Add a fixture that exercises placeholder pass-through**

Create `microservices/config-server/src/test/resources/test-config/demo-app.yml`:

```yaml
demo:
  plain: a-plain-value
  from-env: ${DEMO_ENV_VALUE}
```

- [ ] **Step 2: Write the failing test**

Create `microservices/config-server/src/test/java/com/awbd/cinema/EncryptionEndpointTest.java`:

```java
package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.search-locations=classpath:/test-config",
                "encrypt.key=test-symmetric-encrypt-key-1234567890",
                "management.endpoints.web.exposure.include=*"
        }
)
class EncryptionEndpointTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void encryptThenDecryptRoundTrips() {
        String plaintext = "super-secret-value";

        ResponseEntity<String> encrypted = rest.postForEntity("/encrypt", plaintext, String.class);
        assertThat(encrypted.getStatusCode().is2xxSuccessful()).isTrue();
        String cipher = encrypted.getBody();
        assertThat(cipher).isNotBlank().isNotEqualTo(plaintext);

        ResponseEntity<String> decrypted = rest.postForEntity("/decrypt", cipher, String.class);
        assertThat(decrypted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(decrypted.getBody()).isEqualTo(plaintext);
    }

    @Test
    void servesNativeConfigAndPassesPlaceholdersThrough() {
        ResponseEntity<String> response = rest.getForEntity("/demo-app/default", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("a-plain-value");
        // The JSON Environment endpoint must NOT resolve placeholders: clients do.
        assertThat(response.getBody()).contains("${DEMO_ENV_VALUE}");
    }
}
```

- [ ] **Step 3: Run the test to verify behavior**

Run: `mvn -o -pl config-server test`
Expected: PASS. (If `encryptThenDecryptRoundTrips` fails with a 404/"No encryption", the `encrypt.key` property is not being picked up — confirm it is set in the test `properties`.)

- [ ] **Step 4: Commit**

```bash
git add microservices/config-server/src/test
git commit -m "test(config-server): cover encryption round-trip and placeholder pass-through"
```

---

## Task 3: Author the production config files

**Files:**
- Create: `microservices/config-server/config/application.yml`
- Create: `microservices/config-server/config/user-service.yml`
- Create: `microservices/config-server/config/catalog-service.yml`
- Create: `microservices/config-server/config/booking-service.yml`
- Create: `microservices/config-server/config/gateway.yml`

> These files are served to clients. `{cipher}` values must be generated with the **same `ENCRYPT_KEY`** that will be in `.env` (Task 9). Steps 1–2 generate them.

- [ ] **Step 1: Choose an `ENCRYPT_KEY` and start the server locally to encrypt secrets**

Pick a strong symmetric key (used in `.env` later), then run the config server with it (PowerShell):

```powershell
$env:ENCRYPT_KEY = "replace-with-a-strong-symmetric-key-min-32-chars"
$env:CONFIG_SEARCH_LOCATIONS = "file:./microservices/config-server/config"
mvn -o -pl config-server -am spring-boot:run
```

Leave it running and use a second terminal for Step 2. (The `config/` dir may be empty at this point; `/encrypt` does not need it.)

- [ ] **Step 2: Encrypt the two secrets**

Using the current plaintext values from `.env` (`JWT_SECRET_KEY` and `TMDB_API_KEY`):

```powershell
curl.exe -s -X POST http://localhost:8888/encrypt -d "<paste current JWT_SECRET_KEY value>"
curl.exe -s -X POST http://localhost:8888/encrypt -d "<paste current TMDB_API_KEY value>"
```

Copy each returned ciphertext. Then stop the server (Ctrl+C). You will paste these as `{cipher}<...>` in the files below.

- [ ] **Step 3: Create the shared `application.yml`**

Create `microservices/config-server/config/application.yml` (paste the JWT ciphertext where shown):

```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}

jwt:
  secret:
    key: '{cipher}REPLACE_WITH_ENCRYPTED_JWT_SECRET'

auth:
  cookie:
    secure: ${SECURITY_COOKIE_SECURE:true}
    same-site: ${SECURITY_COOKIE_SAME_SITE:LAX}

security:
  csrf:
    enabled: ${SECURITY_CSRF_ENABLED:true}
  max-attempts: ${SECURITY_MAX_ATTEMPTS:5}
  website:
    domain: ${SECURITY_WEBSITE_DOMAIN:http://localhost:4200}
  cors:
    allowed-origins: ${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 10

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,busrefresh

logging:
  logback:
    rollingpolicy:
      max-file-size: 10MB
      total-size-cap: 100MB
  threshold:
    file: ERROR
  level:
    com.awbd.cinema: DEBUG
    com.netflix.discovery: INFO
    org.springframework.cloud.loadbalancer: DEBUG
```

- [ ] **Step 4: Create `user-service.yml`**

Create `microservices/config-server/config/user-service.yml`:

```yaml
server:
  port: 8081
  servlet:
    context-path: /api/v1

spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}

logging:
  file:
    name: logs/user-service.log
```

- [ ] **Step 5: Create `catalog-service.yml`**

Create `microservices/config-server/config/catalog-service.yml` (paste the TMDB ciphertext where shown):

```yaml
server:
  port: 8082
  servlet:
    context-path: /api/v1

spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}

tmdb:
  api:
    key: '{cipher}REPLACE_WITH_ENCRYPTED_TMDB_KEY'

logging:
  file:
    name: logs/catalog-service.log
```

- [ ] **Step 6: Create `booking-service.yml`**

Create `microservices/config-server/config/booking-service.yml`:

```yaml
server:
  port: 8083
  servlet:
    context-path: /api/v1

spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}

logging:
  file:
    name: logs/booking-service.log
```

- [ ] **Step 7: Create `gateway.yml`**

Create `microservices/config-server/config/gateway.yml`:

```yaml
server:
  port: 8080

gateway:
  rate-limit:
    replenish-rate: ${GATEWAY_RATE_LIMIT_REPLENISH:50}
    burst-capacity: ${GATEWAY_RATE_LIMIT_BURST:100}

logging:
  level:
    com.awbd.cinema: INFO
```

- [ ] **Step 8: Commit**

```bash
git add microservices/config-server/config
git commit -m "feat(config-server): add served config with encrypted JWT/TMDB secrets"
```

---

## Task 4: Convert user-service to a config client

**Files:**
- Modify: `microservices/user-service/pom.xml`
- Modify: `microservices/user-service/src/main/resources/application.properties`
- Modify: `microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java:86`
- Modify: `microservices/user-service/src/test/resources/application.properties`

- [ ] **Step 1: Add config client, bus, and retry dependencies**

In `microservices/user-service/pom.xml`, add inside `<dependencies>` (next to the other `spring-cloud-starter-*` entries):

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Trim the main application.properties to a bootstrap**

Replace the entire contents of `microservices/user-service/src/main/resources/application.properties` with:

```properties
spring.application.name=user-service
spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}
spring.cloud.config.fail-fast=true
spring.cloud.config.retry.max-attempts=20
spring.cloud.config.retry.initial-interval=2000

bootstrap.owner-username=${BOOTSTRAP_OWNER_USERNAME}
bootstrap.owner-password=${BOOTSTRAP_OWNER_PASSWORD}
bootstrap.owner-email=${BOOTSTRAP_OWNER_EMAIL}
bootstrap.owner-first-name=${BOOTSTRAP_OWNER_FIRST_NAME}
bootstrap.owner-last-name=${BOOTSTRAP_OWNER_LAST_NAME}
bootstrap.owner-phone-number=${BOOTSTRAP_OWNER_PHONE_NUMBER}
```

- [ ] **Step 3: CSRF-exempt the actuator endpoints**

In `microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`, change line 86:

```java
                    .ignoringRequestMatchers("/auth/**", "/internal/**")
```

to:

```java
                    .ignoringRequestMatchers("/auth/**", "/internal/**", "/actuator/**")
```

- [ ] **Step 4: Disable config client + bus in tests**

Append to `microservices/user-service/src/test/resources/application.properties`:

```properties
spring.cloud.config.enabled=false
spring.cloud.bus.enabled=false
```

- [ ] **Step 5: Run the user-service tests**

Run: `mvn -o -pl user-service -am test`
Expected: BUILD SUCCESS. All existing user-service tests pass (test config shadows the new bootstrap; config client and bus are disabled, so no config server / RabbitMQ is contacted).

- [ ] **Step 6: Commit**

```bash
git add microservices/user-service
git commit -m "feat(user-service): become a config client with bus refresh"
```

---

## Task 5: Convert catalog-service to a config client

**Files:**
- Modify: `microservices/catalog-service/pom.xml`
- Modify: `microservices/catalog-service/src/main/resources/application.properties`
- Modify: `microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java:77`
- Modify: `microservices/catalog-service/src/test/resources/application.properties`

> `catalog-service/src/main/resources/application.yml` (the `app.cache.*` block) is left untouched — it stays local (see Notes #2).

- [ ] **Step 1: Add config client, bus, and retry dependencies**

In `microservices/catalog-service/pom.xml`, add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Trim the main application.properties to a bootstrap**

Replace the entire contents of `microservices/catalog-service/src/main/resources/application.properties` with:

```properties
spring.application.name=catalog-service
spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}
spring.cloud.config.fail-fast=true
spring.cloud.config.retry.max-attempts=20
spring.cloud.config.retry.initial-interval=2000
```

- [ ] **Step 3: CSRF-exempt the actuator endpoints**

In `microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`, change line 77:

```java
                    .ignoringRequestMatchers("/internal/**")
```

to:

```java
                    .ignoringRequestMatchers("/internal/**", "/actuator/**")
```

- [ ] **Step 4: Disable config client + bus in tests**

Append to `microservices/catalog-service/src/test/resources/application.properties`:

```properties
spring.cloud.config.enabled=false
spring.cloud.bus.enabled=false
```

- [ ] **Step 5: Run the catalog-service tests**

Run: `mvn -o -pl catalog-service -am test`
Expected: BUILD SUCCESS. All existing catalog-service tests pass.

- [ ] **Step 6: Commit**

```bash
git add microservices/catalog-service
git commit -m "feat(catalog-service): become a config client with bus refresh"
```

---

## Task 6: Convert booking-service to a config client

**Files:**
- Modify: `microservices/booking-service/pom.xml`
- Modify: `microservices/booking-service/src/main/resources/application.properties`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java:77`
- Modify: `microservices/booking-service/src/test/resources/application.properties`

> `booking-service/src/main/resources/application.yml` (the `app.cache.*` block) is left untouched — it stays local.

- [ ] **Step 1: Add config client, bus, and retry dependencies**

In `microservices/booking-service/pom.xml`, add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Trim the main application.properties to a bootstrap**

Replace the entire contents of `microservices/booking-service/src/main/resources/application.properties` with:

```properties
spring.application.name=booking-service
spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}
spring.cloud.config.fail-fast=true
spring.cloud.config.retry.max-attempts=20
spring.cloud.config.retry.initial-interval=2000
```

- [ ] **Step 3: CSRF-exempt the actuator endpoints**

In `microservices/booking-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`, change line 77:

```java
                    .ignoringRequestMatchers("/internal/**")
```

to:

```java
                    .ignoringRequestMatchers("/internal/**", "/actuator/**")
```

- [ ] **Step 4: Disable config client + bus in tests**

Append to `microservices/booking-service/src/test/resources/application.properties`:

```properties
spring.cloud.config.enabled=false
spring.cloud.bus.enabled=false
```

- [ ] **Step 5: Run the booking-service tests**

Run: `mvn -o -pl booking-service -am test`
Expected: BUILD SUCCESS. All existing booking-service tests pass.

- [ ] **Step 6: Commit**

```bash
git add microservices/booking-service
git commit -m "feat(booking-service): become a config client with bus refresh"
```

---

## Task 7: Convert gateway to a config client

**Files:**
- Modify: `microservices/gateway/pom.xml`
- Modify: `microservices/gateway/src/main/resources/application.yml`
- Create: `microservices/gateway/src/test/resources/application.yml`

> The gateway has no Spring Security, so there is no CSRF change here.

- [ ] **Step 1: Add config client, bus, and retry dependencies**

In `microservices/gateway/pom.xml`, add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 2: Trim the main application.yml to a bootstrap**

Replace the entire contents of `microservices/gateway/src/main/resources/application.yml` with:

```yaml
spring:
  application:
    name: gateway
  config:
    import: configserver:${CONFIG_SERVER_URL:http://localhost:8888}
  cloud:
    config:
      fail-fast: true
      retry:
        max-attempts: 20
        initial-interval: 2000
```

- [ ] **Step 3: Create a test override so the gateway test runs offline**

Create `microservices/gateway/src/test/resources/application.yml`:

```yaml
spring:
  application:
    name: gateway
  cloud:
    config:
      enabled: false
    bus:
      enabled: false
  data:
    redis:
      host: localhost
      port: 6379

gateway:
  rate-limit:
    replenish-rate: 50
    burst-capacity: 100

eureka:
  client:
    enabled: false
```

- [ ] **Step 4: Run the gateway test**

Run: `mvn -o -pl gateway -am test`
Expected: BUILD SUCCESS. `GatewayApplicationTest.routesAreConfigured` passes (the test `application.yml` shadows the bootstrap, disabling config import and bus; redis + rate-limit values let the route/rate-limiter beans wire up).

- [ ] **Step 5: Commit**

```bash
git add microservices/gateway
git commit -m "feat(gateway): become a config client and primary busrefresh trigger"
```

---

## Task 8: Wire config-server and RabbitMQ into Docker Compose

**Files:**
- Modify: `docker-compose.microservices.yml`

- [ ] **Step 1: Add the `config-server` and `rabbitmq` services**

In `docker-compose.microservices.yml`, add these two services at the top of the `services:` block (after the `redis:` service, before `discovery-server:`):

```yaml
  config-server:
    <<: *ms-common
    container_name: ms-config-server
    command: sh -c "mvn -o -pl config-server spring-boot:run"
    environment:
      SERVER_PORT: 8888
      SPRING_PROFILES_ACTIVE: native
      CONFIG_SEARCH_LOCATIONS: file:/config
    volumes:
      - ./microservices/config-server/config:/config:ro
    ports:
      - "8888:8888"
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8888/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 120s
    networks:
      - microservices-network

  rabbitmq:
    image: rabbitmq:3-management
    container_name: ms-rabbitmq
    ports:
      - "15672:15672"
    healthcheck:
      test: ["CMD-SHELL", "rabbitmq-diagnostics -q ping || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 20
    networks:
      - microservices-network
```

(`ENCRYPT_KEY` reaches the config server through the shared `env_file: .env` in `*ms-common`.)

- [ ] **Step 2: Wire the three business services to config-server + rabbitmq**

For **each** of `user-service`, `catalog-service`, `booking-service` in `docker-compose.microservices.yml`:

(a) Add two env vars under `environment:`:

```yaml
      CONFIG_SERVER_URL: http://config-server:8888
      SPRING_RABBITMQ_HOST: rabbitmq
```

(b) Add two entries under `depends_on:`:

```yaml
      config-server:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
```

- [ ] **Step 3: Wire the gateway to config-server + rabbitmq**

In the `gateway:` service, add under `environment:`:

```yaml
      CONFIG_SERVER_URL: http://config-server:8888
      SPRING_RABBITMQ_HOST: rabbitmq
```

and under `depends_on:`:

```yaml
      config-server:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
```

- [ ] **Step 4: Validate the compose file parses**

Run: `docker compose -f docker-compose.microservices.yml config > /dev/null && echo OK`
Expected: prints `OK` (no YAML/compose errors).

- [ ] **Step 5: Commit**

```bash
git add docker-compose.microservices.yml
git commit -m "feat(compose): add config-server and rabbitmq, wire clients"
```

---

## Task 9: Update environment files

**Files:**
- Modify: `.env`
- Modify: `.env.example`

- [ ] **Step 1: Update `.env`**

In `.env`:
- Add a line: `ENCRYPT_KEY=replace-with-a-strong-symmetric-key-min-32-chars` (use the **same** key chosen in Task 3 Step 1).
- Remove the `JWT_SECRET_KEY=...` line (now `{cipher}`-encrypted in the config server).
- Remove the `TMDB_API_KEY=...` line (now `{cipher}`-encrypted in the config server).
- Leave `DATABASE_*`, `SECURITY_*`, `BOOTSTRAP_OWNER_*`, and `GATEWAY_*` unchanged.

- [ ] **Step 2: Update `.env.example`**

In `.env.example`, mirror the structure: add `ENCRYPT_KEY=` with a placeholder/comment, and remove `JWT_SECRET_KEY` / `TMDB_API_KEY` from the service-facing section. Add a short comment noting these two secrets now live as `{cipher}` values in `microservices/config-server/config/`.

- [ ] **Step 3: Commit**

```bash
git add .env.example
git commit -m "chore(env): add ENCRYPT_KEY, drop service secrets now encrypted in config"
```

(Note: `.env` is gitignored and is not committed; only `.env.example` is tracked. Verify with `git status` that `.env` does not appear.)

---

## Task 10: Document the config server in the README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add a "Configuration (Spring Cloud Config)" section**

Add a section to `README.md` covering:

- **Overview:** config server on `:8888`, native backend, files in `microservices/config-server/config/`, mounted as a volume so host edits are served without a restart.
- **Secrets:** `JWT_SECRET_KEY` and `TMDB_API_KEY` are `{cipher}`-encrypted with the symmetric `ENCRYPT_KEY` (from `.env`). To (re)generate a value:
  ```
  curl -X POST http://localhost:8888/encrypt -d 'the-secret-value'
  ```
  Paste the result as `'{cipher}<...>'` into the relevant file.
- **Live refresh demo:** edit `logging.level.com.awbd.cinema` in `microservices/config-server/config/application.yml`, then:
  ```
  curl -X POST http://localhost:8080/actuator/busrefresh
  ```
  All services rebind with no restart; watch the broadcast in the RabbitMQ UI at `http://localhost:15672` (guest/guest).
- **Default owner login (first run):** document the `BOOTSTRAP_OWNER_USERNAME` / `BOOTSTRAP_OWNER_PASSWORD` values from `.env` (it is a one-time DB seed, not refreshable).
- **Security caveat:** the config server's `/decrypt` endpoint is reachable while port 8888 is mapped to the host; disable it or keep 8888 off-host in a real deployment.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document config server, encryption, and live refresh"
```

---

## Task 11: Full build and end-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Full reactor build**

Run: `mvn -o -f microservices/pom.xml clean test`
Expected: BUILD SUCCESS across all modules (config-server, common, discovery-server, user/catalog/booking-service, gateway).

- [ ] **Step 2: Bring up the full stack**

Run: `docker compose -f docker-compose.microservices.yml up --build`
Expected: `ms-config-server` and `ms-rabbitmq` become healthy first; each service logs that it fetched config from the config server at startup; all register in Eureka (`http://localhost:8761`).

- [ ] **Step 3: Verify a service received decrypted config**

Confirm the config server serves a decrypted secret (not `{cipher}`):

```
curl http://localhost:8888/user-service/default
```
Expected: the JSON contains the plaintext `jwt.secret.key` value (decrypted), and infra placeholders like `${DATABASE_URL}` appear literally (resolved by the client, not the server).

- [ ] **Step 4: Smoke-test the application end to end**

Through the gateway (`http://localhost:8080/api/v1/...`) and/or the Angular client (`http://localhost:4200`): log in as the bootstrap owner and exercise a catalog + booking flow. Expected: behaves exactly as before the change.

- [ ] **Step 5: Demonstrate live refresh (the headline feature)**

1. Edit `microservices/config-server/config/application.yml`: change `logging.level.com.awbd.cinema` from `DEBUG` to `INFO`.
2. Trigger the broadcast:
   ```
   curl -X POST http://localhost:8080/actuator/busrefresh
   ```
3. Expected: the RabbitMQ UI shows the refresh message; service logs show the level change taking effect with **no restart**. Reverting the value + `busrefresh` again flips it back.

- [ ] **Step 6: Final commit (if any verification fixes were needed)**

```bash
git add -A
git commit -m "chore: config server end-to-end verification fixes"
```

---

## Self-review checklist (completed by plan author)

- **Spec coverage:** config server module (T1), encryption (T2/T3/T9), all services as clients (T4–T7), bus refresh + endpoint exposure + CSRF exemption (T4–T8), gateway primary trigger (T7), compose + RabbitMQ (T8), `.env` (T9), README incl. default owner + `/decrypt` caveat (T10), tests + live-refresh demo (T11). Bootstrap owner password intentionally kept local (T4 Step 2). ✓
- **Placeholder scan:** the only `REPLACE_WITH_*` tokens are the `{cipher}` values, which T3 Steps 1–2 generate explicitly at runtime (ciphertext is non-deterministic and cannot be hardcoded). ✓
- **Type/name consistency:** `CONFIG_SERVER_URL`, `SPRING_RABBITMQ_HOST`, `ENCRYPT_KEY`, `CONFIG_SEARCH_LOCATIONS`, and `spring.config.import=configserver:${CONFIG_SERVER_URL:...}` are used identically across all module configs and the compose file. ✓
