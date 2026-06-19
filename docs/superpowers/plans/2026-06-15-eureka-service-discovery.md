# Eureka Service Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a functional Netflix Eureka service registry so the four microservices discover each other by name (no hardcoded inter-service URLs), with the discovery demonstrated via the Eureka dashboard and application logs.

**Architecture:** A new standalone `discovery-server` (Eureka Server) module joins the existing Maven reactor. `user-service`, `catalog-service`, `booking-service`, and `gateway` become Eureka clients. The three Feign clients drop their hardcoded `url` and resolve targets by service name (with `path="/api/v1"` to preserve the context-path); the gateway routes switch from static URIs to `lb://service-name`. Docker Compose gains the registry and wires every app to it.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2 (`spring-cloud-starter-netflix-eureka-server`/`-client`, Spring Cloud LoadBalancer transitive), OpenFeign, Spring Cloud Gateway (WebFlux), Docker Compose, Maven 3.9 / Java 21.

**Reference spec:** `docs/superpowers/specs/2026-06-15-eureka-service-discovery-design.md`

---

## File Structure

**New files**
- `microservices/discovery-server/pom.xml` — Eureka Server module.
- `microservices/discovery-server/src/main/java/com/awbd/cinema/DiscoveryServerApplication.java` — `@EnableEurekaServer` boot class.
- `microservices/discovery-server/src/main/resources/application.yml` — registry config (port 8761, standalone).
- `microservices/discovery-server/src/test/java/com/awbd/cinema/DiscoveryServerApplicationTest.java` — context-load test.

**Modified files**
- `microservices/pom.xml` — register the new module.
- `microservices/{user,catalog,booking,gateway}-service*/pom.xml` — add eureka-client dependency (gateway pom is `microservices/gateway/pom.xml`).
- `microservices/user-service/src/main/resources/application.properties` + test props — eureka config; remove `services.booking.url`.
- `microservices/catalog-service/src/main/resources/application.properties` + test props — eureka config.
- `microservices/booking-service/src/main/resources/application.properties` + test props — eureka config; remove `services.catalog.url`, `services.user.url`.
- `microservices/gateway/src/main/resources/application.yml` — eureka config; remove `services:` block.
- `microservices/gateway/src/main/java/com/awbd/cinema/config/RouteConfig.java` — `lb://` URIs.
- `microservices/gateway/src/test/java/com/awbd/cinema/GatewayApplicationTest.java` — disable eureka in test.
- The three Feign clients — drop `url`, add `path="/api/v1"`.
- `docker-compose.microservices.yml` — add `discovery-server`, wire `EUREKA_SERVER_URL` + `depends_on`, remove obsolete service-URL/URI envs.
- `README.md` — "Service Discovery (Eureka)" section.

**Verification convention:** all Maven commands are run from the repo root using `-f microservices/pom.xml`. Maven 3.9.11 runs on Java 21 here. `-am` ("also make") builds the `common` dependency first for service modules.

---

### Task 1: `discovery-server` module (Eureka Server)

**Files:**
- Create: `microservices/discovery-server/pom.xml`
- Create: `microservices/discovery-server/src/main/java/com/awbd/cinema/DiscoveryServerApplication.java`
- Create: `microservices/discovery-server/src/main/resources/application.yml`
- Create: `microservices/discovery-server/src/test/java/com/awbd/cinema/DiscoveryServerApplicationTest.java`
- Modify: `microservices/pom.xml`

- [ ] **Step 1: Create the module pom**

Create `microservices/discovery-server/pom.xml`:

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

    <artifactId>discovery-server</artifactId>
    <packaging>jar</packaging>
    <name>discovery-server</name>
    <description>Netflix Eureka service registry</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
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

(`spring-boot-starter-actuator` is inherited from the parent `<dependencies>`, so the healthcheck endpoint is available without declaring it here.)

- [ ] **Step 2: Register the module in the parent reactor**

In `microservices/pom.xml`, change:

```xml
    <modules>
        <module>common</module>
```

to:

```xml
    <modules>
        <module>discovery-server</module>
        <module>common</module>
```

- [ ] **Step 3: Create the application config**

Create `microservices/discovery-server/src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:8761}

spring:
  application:
    name: discovery-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  instance:
    hostname: ${EUREKA_INSTANCE_HOSTNAME:localhost}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 4: Write the context-load test**

Create `microservices/discovery-server/src/test/java/com/awbd/cinema/DiscoveryServerApplicationTest.java`:

```java
package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "server.port=0")
class DiscoveryServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -f microservices/pom.xml -pl discovery-server test`
Expected: FAIL — compilation error, `cannot find symbol ... DiscoveryServerApplication` (the boot class does not exist yet).

- [ ] **Step 6: Create the Eureka Server boot class**

Create `microservices/discovery-server/src/main/java/com/awbd/cinema/DiscoveryServerApplication.java`:

```java
package com.awbd.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -f microservices/pom.xml -pl discovery-server test`
Expected: PASS — `BUILD SUCCESS`, `DiscoveryServerApplicationTest.contextLoads` green. (The server starts standalone on a random port; `register-with-eureka=false` means no outbound calls.)

- [ ] **Step 8: Commit**

```bash
git add microservices/pom.xml microservices/discovery-server
git commit -m "feat(discovery): add Eureka service registry module"
```

---

### Task 2: `user-service` as Eureka client + name-based notification Feign client

**Files:**
- Modify: `microservices/user-service/pom.xml`
- Modify: `microservices/user-service/src/main/resources/application.properties`
- Modify: `microservices/user-service/src/test/resources/application.properties`
- Modify: `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClient.java`

- [ ] **Step 1: Add the Eureka client dependency**

In `microservices/user-service/pom.xml`, immediately after the `spring-cloud-starter-circuitbreaker-resilience4j` dependency:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
```

add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
```

- [ ] **Step 2: Add Eureka config to main properties and remove the old service URL**

In `microservices/user-service/src/main/resources/application.properties`:

Remove this line:

```properties
services.booking.url=${BOOKING_SERVICE_URL:http://localhost:8083/api/v1}
```

And add these two lines just below `spring.cloud.openfeign.circuitbreaker.enabled=true`:

```properties
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
eureka.instance.prefer-ip-address=true
```

- [ ] **Step 3: Disable Eureka in the test properties and remove the old service URL**

In `microservices/user-service/src/test/resources/application.properties`:

Remove this line:

```properties
services.booking.url=http://localhost:8083/api/v1
```

And add this line at the end of the file:

```properties
eureka.client.enabled=false
```

- [ ] **Step 4: Switch the Feign client to name-based resolution**

Replace the `@FeignClient` annotation in `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClient.java`:

```java
@FeignClient(
        name = "booking-service",
        url = "${services.booking.url}",
        fallback = NotificationServiceClientFallback.class
)
```

with:

```java
@FeignClient(
        name = "booking-service",
        path = "/api/v1",
        fallback = NotificationServiceClientFallback.class
)
```

- [ ] **Step 5: Run the full module test suite**

Run: `mvn -f microservices/pom.xml -pl user-service -am test`
Expected: PASS — `BUILD SUCCESS`. The Feign client is a `@MockitoBean`/`@Mock` in every test, so dropping `url` does not affect them; `eureka.client.enabled=false` keeps the context from attempting registration.

- [ ] **Step 6: Commit**

```bash
git add microservices/user-service
git commit -m "feat(user-service): register with Eureka, resolve booking-service by name"
```

---

### Task 3: `catalog-service` as Eureka client

**Files:**
- Modify: `microservices/catalog-service/pom.xml`
- Modify: `microservices/catalog-service/src/main/resources/application.properties`
- Modify: `microservices/catalog-service/src/test/resources/application.properties`

- [ ] **Step 1: Add the Eureka client dependency**

In `microservices/catalog-service/pom.xml`, immediately after the `spring-boot-configuration-processor` dependency block:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
```

add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
```

- [ ] **Step 2: Add Eureka config to main properties**

In `microservices/catalog-service/src/main/resources/application.properties`, add these two lines just below `spring.data.redis.port=${REDIS_PORT:6379}`:

```properties
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
eureka.instance.prefer-ip-address=true
```

- [ ] **Step 3: Disable Eureka in the test properties**

In `microservices/catalog-service/src/test/resources/application.properties`, add this line at the end of the file:

```properties
eureka.client.enabled=false
```

- [ ] **Step 4: Run the full module test suite**

Run: `mvn -f microservices/pom.xml -pl catalog-service -am test`
Expected: PASS — `BUILD SUCCESS`. catalog-service has no Feign client; this task only makes it a registering client.

- [ ] **Step 5: Commit**

```bash
git add microservices/catalog-service
git commit -m "feat(catalog-service): register with Eureka"
```

---

### Task 4: `booking-service` as Eureka client + name-based Feign clients

**Files:**
- Modify: `microservices/booking-service/pom.xml`
- Modify: `microservices/booking-service/src/main/resources/application.properties`
- Modify: `microservices/booking-service/src/test/resources/application.properties`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClient.java`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClient.java`

- [ ] **Step 1: Add the Eureka client dependency**

In `microservices/booking-service/pom.xml`, immediately after the `spring-cloud-starter-circuitbreaker-resilience4j` dependency:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
```

add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
```

- [ ] **Step 2: Add Eureka config to main properties and remove the old service URLs**

In `microservices/booking-service/src/main/resources/application.properties`:

Remove these two lines:

```properties
services.catalog.url=${CATALOG_SERVICE_URL:http://localhost:8082/api/v1}
services.user.url=${USER_SERVICE_URL:http://localhost:8081/api/v1}
```

And add these two lines just below `spring.cloud.openfeign.circuitbreaker.enabled=true`:

```properties
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
eureka.instance.prefer-ip-address=true
```

- [ ] **Step 3: Disable Eureka in the test properties and remove the old service URLs**

In `microservices/booking-service/src/test/resources/application.properties`:

Remove these two lines:

```properties
services.catalog.url=http://localhost:8082/api/v1
services.user.url=http://localhost:8081/api/v1
```

And add this line at the end of the file:

```properties
eureka.client.enabled=false
```

- [ ] **Step 4: Switch CatalogServiceClient to name-based resolution**

Replace the `@FeignClient` annotation in `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClient.java`:

```java
@FeignClient(
        name = "catalog-service",
        url = "${services.catalog.url}",
        fallback = CatalogServiceClientFallback.class
)
```

with:

```java
@FeignClient(
        name = "catalog-service",
        path = "/api/v1",
        fallback = CatalogServiceClientFallback.class
)
```

- [ ] **Step 5: Switch UserServiceClient to name-based resolution**

Replace the `@FeignClient` annotation in `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClient.java`:

```java
@FeignClient(
        name = "user-service",
        url = "${services.user.url}",
        fallback = UserServiceClientFallback.class
)
```

with:

```java
@FeignClient(
        name = "user-service",
        path = "/api/v1",
        fallback = UserServiceClientFallback.class
)
```

- [ ] **Step 6: Run the full module test suite**

Run: `mvn -f microservices/pom.xml -pl booking-service -am test`
Expected: PASS — `BUILD SUCCESS`. Both Feign clients are mocked in every test, so dropping `url` is safe; `eureka.client.enabled=false` prevents registration during `@SpringBootTest`.

- [ ] **Step 7: Commit**

```bash
git add microservices/booking-service
git commit -m "feat(booking-service): register with Eureka, resolve catalog/user services by name"
```

---

### Task 5: `gateway` as Eureka client + `lb://` routes

**Files:**
- Modify: `microservices/gateway/pom.xml`
- Modify: `microservices/gateway/src/main/resources/application.yml`
- Modify: `microservices/gateway/src/main/java/com/awbd/cinema/config/RouteConfig.java`
- Modify: `microservices/gateway/src/test/java/com/awbd/cinema/GatewayApplicationTest.java`

- [ ] **Step 1: Add the Eureka client dependency**

In `microservices/gateway/pom.xml`, immediately after the `spring-boot-starter-data-redis-reactive` dependency:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
```

add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
```

(`spring-cloud-starter-loadbalancer`, needed for the reactive `lb://` scheme, comes in transitively via the eureka client starter.)

- [ ] **Step 2: Replace the gateway application.yml**

Replace the entire contents of `microservices/gateway/src/main/resources/application.yml` with:

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

gateway:
  rate-limit:
    replenish-rate: ${GATEWAY_RATE_LIMIT_REPLENISH:50}
    burst-capacity: ${GATEWAY_RATE_LIMIT_BURST:100}

management:
  endpoints:
    web:
      exposure:
        include: health,info

logging:
  level:
    com.awbd.cinema: INFO
```

(The `services:` block is removed; route targets now come from `RouteConfig`.)

- [ ] **Step 3: Switch routes to `lb://` in RouteConfig**

Replace the entire contents of `microservices/gateway/src/main/java/com/awbd/cinema/config/RouteConfig.java` with:

```java
package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, @Qualifier("ipKeyResolver") KeyResolver keyResolver) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/user/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://user-service"))
                .route("catalog-service", r -> r
                        .path("/api/v1/movies/**", "/api/v1/rooms/**", "/api/v1/seats/**", "/api/v1/screen-sessions/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://catalog-service"))
                .route("booking-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/tickets/**", "/api/v1/ticket-info/**", "/api/v1/offers/**", "/api/v1/notifications/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://booking-service"))
                .build();
    }
}
```

- [ ] **Step 4: Disable Eureka in the gateway test**

Replace the class annotation in `microservices/gateway/src/test/java/com/awbd/cinema/GatewayApplicationTest.java`:

```java
@SpringBootTest
class GatewayApplicationTest {
```

with:

```java
@SpringBootTest(properties = "eureka.client.enabled=false")
class GatewayApplicationTest {
```

- [ ] **Step 5: Run the gateway test suite**

Run: `mvn -f microservices/pom.xml -pl gateway test`
Expected: PASS — `BUILD SUCCESS`, `GatewayApplicationTest.routesAreConfigured` still asserts 3 routes. (Routes build with `lb://` URIs; no resolution occurs in the test, and Eureka is disabled.)

- [ ] **Step 6: Commit**

```bash
git add microservices/gateway
git commit -m "feat(gateway): register with Eureka, route via lb:// service names"
```

---

### Task 6: Docker Compose wiring

**Files:**
- Modify: `docker-compose.microservices.yml`

- [ ] **Step 1: Add the `discovery-server` service**

In `docker-compose.microservices.yml`, add this block immediately after the `redis:` service block (after its lines, before `user-service:`):

```yaml
  discovery-server:
    <<: *ms-common
    container_name: ms-discovery-server
    command: sh -c "mvn -o -pl discovery-server spring-boot:run"
    environment:
      SERVER_PORT: 8761
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 120s
    networks:
      - microservices-network
```

- [ ] **Step 2: Wire `user-service` to Eureka**

In the `user-service` service, change its `environment:` and `depends_on:` blocks.

Replace:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://user-db:5432/${USER_DB_NAME}
      BOOKING_SERVICE_URL: http://booking-service:8080/api/v1
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8081:8080"
    depends_on:
      user-db:
        condition: service_healthy
```

with:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://user-db:5432/${USER_DB_NAME}
      EUREKA_SERVER_URL: http://discovery-server:8761/eureka/
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8081:8080"
    depends_on:
      user-db:
        condition: service_healthy
      discovery-server:
        condition: service_healthy
```

- [ ] **Step 3: Wire `catalog-service` to Eureka**

In the `catalog-service` service, replace:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://catalog-db:5432/${CATALOG_DB_NAME}
      REDIS_HOST: redis
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8082:8080"
    depends_on:
      catalog-db:
        condition: service_healthy
      redis:
        condition: service_started
```

with:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://catalog-db:5432/${CATALOG_DB_NAME}
      REDIS_HOST: redis
      EUREKA_SERVER_URL: http://discovery-server:8761/eureka/
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8082:8080"
    depends_on:
      catalog-db:
        condition: service_healthy
      redis:
        condition: service_started
      discovery-server:
        condition: service_healthy
```

- [ ] **Step 4: Wire `booking-service` to Eureka**

In the `booking-service` service, replace:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://booking-db:5432/${BOOKING_DB_NAME}
      REDIS_HOST: redis
      CATALOG_SERVICE_URL: http://catalog-service:8080/api/v1
      USER_SERVICE_URL: http://user-service:8080/api/v1
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8083:8080"
    depends_on:
      booking-db:
        condition: service_healthy
      redis:
        condition: service_started
```

with:

```yaml
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://booking-db:5432/${BOOKING_DB_NAME}
      REDIS_HOST: redis
      EUREKA_SERVER_URL: http://discovery-server:8761/eureka/
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
    ports:
      - "8083:8080"
    depends_on:
      booking-db:
        condition: service_healthy
      redis:
        condition: service_started
      discovery-server:
        condition: service_healthy
```

- [ ] **Step 5: Wire `gateway` to Eureka**

In the `gateway` service, replace:

```yaml
    environment:
      USER_SERVICE_URI: http://user-service:8080
      CATALOG_SERVICE_URI: http://catalog-service:8080
      BOOKING_SERVICE_URI: http://booking-service:8080
      REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      user-service:
        condition: service_healthy
      catalog-service:
        condition: service_healthy
      booking-service:
        condition: service_healthy
      redis:
        condition: service_started
```

with:

```yaml
    environment:
      EUREKA_SERVER_URL: http://discovery-server:8761/eureka/
      REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      user-service:
        condition: service_healthy
      catalog-service:
        condition: service_healthy
      booking-service:
        condition: service_healthy
      redis:
        condition: service_started
      discovery-server:
        condition: service_healthy
```

- [ ] **Step 6: Validate the compose file**

Run: `docker compose -f docker-compose.microservices.yml config`
Expected: prints the fully-resolved config with no errors; a `discovery-server` service is present and the obsolete `*_SERVICE_URL`/`*_SERVICE_URI` keys are gone.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.microservices.yml
git commit -m "feat(compose): add discovery-server, wire services to Eureka"
```

---

### Task 7: Demonstration — discovery logging + README

**Files:**
- Modify: `microservices/user-service/src/main/resources/application.properties`
- Modify: `microservices/catalog-service/src/main/resources/application.properties`
- Modify: `microservices/booking-service/src/main/resources/application.properties`
- Modify: `microservices/gateway/src/main/resources/application.yml`
- Modify: `README.md`

- [ ] **Step 1: Add discovery log levels to user-service**

In `microservices/user-service/src/main/resources/application.properties`, add these two lines at the end of the file:

```properties
logging.level.com.netflix.discovery=INFO
logging.level.org.springframework.cloud.loadbalancer=DEBUG
```

- [ ] **Step 2: Add discovery log levels to catalog-service**

In `microservices/catalog-service/src/main/resources/application.properties`, add this line at the end of the file:

```properties
logging.level.com.netflix.discovery=INFO
```

(catalog-service makes no outbound Feign calls, so no LoadBalancer logging is needed.)

- [ ] **Step 3: Add discovery log levels to booking-service**

In `microservices/booking-service/src/main/resources/application.properties`, add these two lines at the end of the file:

```properties
logging.level.com.netflix.discovery=INFO
logging.level.org.springframework.cloud.loadbalancer=DEBUG
```

- [ ] **Step 4: Add discovery log levels to the gateway**

In `microservices/gateway/src/main/resources/application.yml`, replace the `logging:` block:

```yaml
logging:
  level:
    com.awbd.cinema: INFO
```

with:

```yaml
logging:
  level:
    com.awbd.cinema: INFO
    com.netflix.discovery: INFO
    org.springframework.cloud.loadbalancer: DEBUG
```

- [ ] **Step 5: Add the README section**

In `README.md`, add this section immediately before the `# Running the Microservices Stack (Docker)` heading:

```markdown
# Service Discovery (Eureka)

The microservices register themselves with a **Netflix Eureka** service registry
(`discovery-server`, host port **8761**) instead of using hardcoded inter-service
URLs. The gateway routes to services by name (`lb://user-service`, etc.) and the
Feign clients resolve their targets by service name through the registry.

## Eureka dashboard

With the stack running, open **http://localhost:8761**. The "Instances currently
registered with Eureka" table lists every running app:
`USER-SERVICE`, `CATALOG-SERVICE`, `BOOKING-SERVICE`, and `GATEWAY`. Stop a
container (`docker stop ms-catalog-service`) and refresh — it disappears from the
table; start it again and it re-registers automatically. This is the live proof
that services discover each other with no static configuration.

## What the logs show

- On startup each app logs its registration, e.g.
  `DiscoveryClient_CATALOG-SERVICE/... - registration status: 204`
  (`com.netflix.discovery` at INFO).
- On each inter-service call, the caller logs the name→instance selection, e.g.
  the gateway and booking-service log LoadBalancer choosing an instance for
  `catalog-service` (`org.springframework.cloud.loadbalancer` at DEBUG).

Because resolution is by service name, no URL changes are needed to move or scale
a service — the registry always reports its current location.
```

- [ ] **Step 6: Verify the demonstration end-to-end**

Run: `docker compose -f docker-compose.microservices.yml up --build`
Wait for all healthchecks to pass (first run is slow — the reactor compiles). Then:

1. Open `http://localhost:8761` and confirm all four instances are listed.
2. Run a smoke request through the gateway (e.g. `curl http://localhost:8080/api/v1/movies`) and confirm it succeeds — proving the gateway resolved `lb://catalog-service` via Eureka.
3. In the logs (`docker compose -f docker-compose.microservices.yml logs catalog-service | grep -i "registration status"`), confirm the registration line appears.

Expected: dashboard shows 4 instances; smoke request returns the normal JSON; registration log lines present.

- [ ] **Step 7: Commit**

```bash
git add microservices README.md
git commit -m "docs(discovery): add Eureka dashboard/log demonstration + README"
```

---

## Notes for the implementer

- **No entity, DTO, controller, or frontend changes.** JSON contracts are unchanged; the gateway still serves `:8080/api/v1`.
- **Why `path = "/api/v1"` on the Feign clients:** Eureka registers only host:port, not the `server.servlet.context-path=/api/v1`. Without `path`, a name-resolved call would drop the context-path and 404. The gateway needs no equivalent because it already forwards the full `/api/v1/...` path.
- **Why `prefer-ip-address: true`:** inside the Docker bridge network, advertising the container IP makes each instance reachable by its peers; advertising the default hostname (container ID) would not resolve.
- **Scope boundary:** this delivers discovery with one instance per service. Running multiple instances and demonstrating load distribution is the separate Load Balancing task (#16).
- **First-call latency:** Eureka's registry/cache refresh can take a few seconds after an instance starts before it is discoverable — expected for a PoC.
