# Microservices Phase 4: API Gateway + Docker Compose Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reactive Spring Cloud Gateway (static routes + in-memory rate limiting + request/response logging) and a `docker-compose.microservices.yml` that brings up the whole new stack — 3 per-service Postgres databases, shared Redis, all 4 Spring Boot services, the gateway, and the Angular client — with a single `docker compose up`, exposing the backend on `http://localhost:8080/api/v1` so the existing frontend works unchanged.

**Architecture:** A fifth reactor module, `gateway` (reactive Spring Cloud Gateway on WebFlux/Netty), routes path-prefixes to the owning service over the Docker network; it has no `common` dependency, no security, no JPA — it just proxies (the `jwt` cookie passes through to each service, which validates it). Rate limiting uses Spring Cloud Gateway's built-in `RequestRateLimiter` filter backed by `RedisRateLimiter` (a per-client token bucket, reusing the shared Redis already in the stack), plus a request/response logging `GlobalFilter`. A single shared `microservices/Dockerfile` builds an image that installs each service's dependencies at image-build time (`mvn install`) so containers start offline and isolated (no shared build dir / `.m2` contention). Each service runs `mvn -o -pl <module> spring-boot:run`, listening on container port 8080 (via a `SERVER_PORT` override), with its own Postgres DB and `/api/v1/actuator/health` healthcheck.

**Tech Stack:** Spring Cloud Gateway (reactive, `spring-cloud-starter-gateway-server-webflux` + `RedisRateLimiter`) on Spring Boot 4.0.7 / Spring Cloud 2025.1.2, Java 21, reactive Redis (Lettuce), Docker + Docker Compose v2, Postgres 15, Redis 7, dotenv-java 3.2.0.

---

## Background & Key Design Decisions

Established from the design spec (`docs/superpowers/specs/2026-06-14-microservices-extraction-design.md` §5.4, §8.1–8.4, §9), the existing monolith `docker-compose.yml`/`cinema/Dockerfile`/`.env.example`, and a verification spike of the gateway stack.

1. **Reactive Spring Cloud Gateway** (`spring-cloud-starter-gateway-server-webflux`) — verified to resolve against the 2025.1.2 BOM and start with routes. A gateway is a pure non-blocking proxy workload (no DB/business logic), and this is the canonical, best-documented SCG flavor. It's a separate process, so its reactive stack doesn't conflict with the servlet-based services, and it's fully transparent to the client (same URLs/cookies/JSON).

2. **Routes via the Java DSL `RouteLocator`** (not YAML), because the programmatic `RouteLocatorBuilder` API is stable across SCG versions (the YAML config namespace changed between Spring Cloud releases). Route target URIs come from properties with local defaults, overridden by env vars in compose. `/internal/**` is deliberately **not** routed (§6.4) — those endpoints are reachable only over the Docker network.

3. **All services listen on container port 8080.** Each service's `application.properties` bakes `server.port=8081/8082/8083`, but compose overrides it with `SERVER_PORT=8080` (Spring relaxed binding), so every service is uniformly `:8080` inside the network; host ports map `8081/8082/8083:8080` for direct debugging, the gateway maps `8080:8080`. Inter-service URLs (Feign + gateway routes) therefore all use container port `8080`.

4. **Per-service databases.** Phase 4 is the first time the services get their **own** Postgres (Phases 1–3 reused one DB). The denormalization done in Phase 3 (plain IDs, no cross-service FKs) is what makes this safe. Each service's `DATABASE_URL` is set in compose to its own DB container; no service code changes — they already read `${DATABASE_URL}`. Credentials (`DATABASE_USER`/`DATABASE_PASSWORD`) are reused across all three DBs; only three new DB-**name** env vars are added.

5. **Image build strategy — `COPY` + `mvn install`, not a live source mount.** The spec sketched mounting the `microservices/` tree into every container and running `mvn -am spring-boot:run`. That breaks with multiple services because they'd share one host-mounted `common/target` and clobber each other's concurrent builds. Instead, the shared `Dockerfile` `COPY`s the reactor and runs `mvn -Dmaven.test.skip=true install` **at image-build time** (populating the image's `.m2` with `common` + all deps). Each container then runs `mvn -o -pl <module> spring-boot:run` — offline, isolated, no shared volumes, no contention. (Trade-off: source changes require an image rebuild — acceptable for a PoC stack.) A `.dockerignore` excludes `**/target` so host build artifacts don't pollute the image.

6. **Actuator healthchecks.** The parent reactor pom already puts `spring-boot-starter-actuator` on every module, and each service exposes `health` via `management.endpoints.web.exposure.include`. Because the services set `server.servlet.context-path=/api/v1`, their actuator health is at `/api/v1/actuator/health`; the gateway (no context path) is at `/actuator/health`. Compose healthchecks `curl -f` these (the Dockerfile installs `curl`). Generous `start_period`/`retries` accommodate first-run in-container compilation.

7. **CORS stays in the services.** Each service already sets `SECURITY_CORS_ALLOWED_ORIGINS=http://localhost:4200` and emits CORS headers; the gateway proxies those through unchanged. The browser's `Origin` header reaches the service regardless of the gateway, so no gateway CORS config is needed (§9: no frontend changes).

8. **Rate limiting via the built-in `RequestRateLimiter` + `RedisRateLimiter`** (§5.4): Spring Cloud Gateway's canonical rate-limiter filter (a Redis-backed token bucket) is attached to every route, keyed per-client-IP via a `KeyResolver` bean; over the configured rate it returns `429`. It reuses the **same Redis** the catalog/booking services already use (the gateway gains a reactive-Redis client). A separate request/response logging `GlobalFilter` logs method/path/status/latency. (SCG ships no built-in *in-memory* limiter — `RedisRateLimiter` is its only production `RateLimiter` — so this is the standard out-of-the-box choice; the shared Redis container makes it cheap. Verified: the gateway's `@SpringBootTest` routes test still loads **without** a running Redis because Lettuce connects lazily, so the offline `mvn clean verify` gate is unaffected. At runtime the gateway `depends_on` Redis.)

---

### Task 1: `gateway` module (reactive Spring Cloud Gateway)

**Files:**
- Modify: `microservices/pom.xml` (add `<module>gateway</module>`)
- Create: `microservices/gateway/pom.xml`
- Create: `microservices/gateway/src/main/java/com/awbd/cinema/GatewayApplication.java`
- Create: `microservices/gateway/src/main/java/com/awbd/cinema/config/RateLimitConfig.java`
- Create: `microservices/gateway/src/main/java/com/awbd/cinema/config/RouteConfig.java`
- Create: `microservices/gateway/src/main/java/com/awbd/cinema/filters/LoggingGlobalFilter.java`
- Create: `microservices/gateway/src/main/resources/application.yml`
- Test: `microservices/gateway/src/test/java/com/awbd/cinema/GatewayApplicationTest.java`

- [ ] **Step 1: Register the module in the reactor**

In `microservices/pom.xml`, change:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
        <module>booking-service</module>
    </modules>
```

to add a fifth line:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
        <module>booking-service</module>
        <module>gateway</module>
    </modules>
```

- [ ] **Step 2: Create `microservices/gateway/pom.xml`**

No `common` dependency, no security, no JPA. Actuator is inherited from the parent reactor pom.

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

    <artifactId>gateway</artifactId>
    <packaging>jar</packaging>
    <name>gateway</name>
    <description>Reactive API gateway (Spring Cloud Gateway, static routes)</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.cdimascio</groupId>
            <artifactId>dotenv-java</artifactId>
            <version>3.2.0</version>
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

- [ ] **Step 3: Create `GatewayApplication`**

```java
package com.awbd.cinema;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 4: Create the rate-limiter beans** (`KeyResolver` + `RedisRateLimiter`)

Create `microservices/gateway/src/main/java/com/awbd/cinema/config/RateLimitConfig.java`:

```java
package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /** Rate-limit per calling client IP. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown");
    }

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${gateway.rate-limit.replenish-rate:50}") int replenishRate,
            @Value("${gateway.rate-limit.burst-capacity:100}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
```

> `replenishRate` = sustained requests/second per client; `burstCapacity` = max burst. Over the limit, the `RequestRateLimiter` filter returns `429 Too Many Requests`.

- [ ] **Step 5: Create the route configuration** (Java DSL — verified against the actual BOM — applying the rate limiter per route)

Create `microservices/gateway/src/main/java/com/awbd/cinema/config/RouteConfig.java`:

```java
package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${services.user.uri}")
    private String userUri;

    @Value("${services.catalog.uri}")
    private String catalogUri;

    @Value("${services.booking.uri}")
    private String bookingUri;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/user/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(userUri))
                .route("catalog-service", r -> r
                        .path("/api/v1/movies/**", "/api/v1/rooms/**", "/api/v1/seats/**", "/api/v1/screen-sessions/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(catalogUri))
                .route("booking-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/tickets/**", "/api/v1/ticket-info/**", "/api/v1/offers/**", "/api/v1/notifications/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(bookingUri))
                .build();
    }
}
```

> No route matches `/api/v1/**/internal/**` or any `/internal/**` path — those stay Docker-network-only (§6.4).

- [ ] **Step 6: Create the request/response logging filter**

Create `microservices/gateway/src/main/java/com/awbd/cinema/filters/LoggingGlobalFilter.java`:

```java
package com.awbd.cinema.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange).then(Mono.fromRunnable(() ->
                log.info("Gateway {} {} -> {} ({} ms)",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - start)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```

- [ ] **Step 7: Create `application.yml`**

Route target URIs and Redis connection have local defaults and are overridden by env vars in compose. The rate limit is generous by default.

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

services:
  user:
    uri: ${USER_SERVICE_URI:http://localhost:8081}
  catalog:
    uri: ${CATALOG_SERVICE_URI:http://localhost:8082}
  booking:
    uri: ${BOOKING_SERVICE_URI:http://localhost:8083}

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

> The reactive Redis client (`spring.data.redis.*`) backs the `RedisRateLimiter`. Lettuce connects lazily, so this does not require Redis to be running at build/test time — only at runtime (the compose gateway `depends_on` Redis).

- [ ] **Step 8: Create the context/routes test**

Create `microservices/gateway/src/test/java/com/awbd/cinema/GatewayApplicationTest.java`:

```java
package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesAreConfigured() {
        assertThat(routeLocator.getRoutes().collectList().block()).hasSize(3);
    }
}
```

- [ ] **Step 9: Build the gateway + full reactor**

From `microservices/` run: `mvn clean verify`
Expected: `BUILD SUCCESS` for all five modules. The gateway's `GatewayApplicationTest` passes (1 test, context loads with 3 routes); the other modules' test totals are unchanged (common 10, user-service 57, catalog-service 121, booking-service 106).

(Maven 3.9.11 + Java 21 on PATH as `mvn`; no `mvnw` inside `microservices/`.)

- [ ] **Step 10: Commit**

```bash
git add microservices/pom.xml microservices/gateway
git commit -m "Add reactive Spring Cloud Gateway: static routes, in-memory rate limiting, request logging"
```

---

### Task 2: Shared `Dockerfile` + `.dockerignore` + `.env.example` additions

One image builds the whole reactor and bakes dependencies in (so containers start offline and isolated). Add the three new DB-name vars to `.env.example`.

**Files:**
- Create: `microservices/Dockerfile`
- Create: `microservices/.dockerignore`
- Modify: `.env.example` (append 3 DB-name vars + the gateway rate-limit var)

- [ ] **Step 1: Create `microservices/Dockerfile`**

`COPY`s the reactor and runs `mvn install` at build time (populating the image's local `.m2` with `common` + all deps), so each container can later run a single module **offline** with no shared build directory. `curl` is installed for the Compose healthchecks.

```dockerfile
FROM maven:3.9.16-eclipse-temurin-21

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY . .

# Build & install all modules (common + the 4 services + gateway) into the image's local
# Maven repository so each container can run its module offline, isolated from the others.
RUN mvn -q -Dmaven.test.skip=true install

EXPOSE 8080
```

- [ ] **Step 2: Create `microservices/.dockerignore`**

Keeps host build output and IDE noise out of the build context (so the in-image `mvn install` builds clean, and the context stays small).

```
**/target
**/*.iml
.idea
logs
```

- [ ] **Step 3: Append the new vars to `.env.example`**

Append these lines to the end of `.env.example` (reusing the existing `DATABASE_USER`/`DATABASE_PASSWORD` for all three new DBs — only the database **names** are new):

```
# --- Microservices stack (docker-compose.microservices.yml) ---
USER_DB_NAME=user_db
CATALOG_DB_NAME=catalog_db
BOOKING_DB_NAME=booking_db
GATEWAY_RATE_LIMIT_REPLENISH=50
GATEWAY_RATE_LIMIT_BURST=100
```

> Note for the operator: the real `.env` must also contain `USER_DB_NAME`/`CATALOG_DB_NAME`/`BOOKING_DB_NAME` for the microservices compose to interpolate. `DATABASE_NAME` (the monolith's single DB) is left untouched.

- [ ] **Step 4: Commit**

```bash
git add microservices/Dockerfile microservices/.dockerignore .env.example
git commit -m "Add shared microservices Dockerfile (.m2-baked image) and .env.example DB-name vars"
```

---

### Task 3: `docker-compose.microservices.yml`

The whole new stack: 3 Postgres DBs + Redis + 4 services + gateway + client. Lives at the repo root (next to the untouched monolith `docker-compose.yml`).

**Files:**
- Create: `docker-compose.microservices.yml`

- [ ] **Step 1: Create `docker-compose.microservices.yml`**

```yaml
name: cinema-microservices

x-ms-common: &ms-common
  build:
    context: ./microservices
    dockerfile: Dockerfile
  working_dir: /workspace
  env_file:
    - .env
  networks:
    - microservices-network

services:
  user-db:
    image: postgres:15
    container_name: ms-user-db
    environment:
      POSTGRES_USER: ${DATABASE_USER}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
      POSTGRES_DB: ${USER_DB_NAME}
    volumes:
      - user_db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB} || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 20
    networks:
      - microservices-network

  catalog-db:
    image: postgres:15
    container_name: ms-catalog-db
    environment:
      POSTGRES_USER: ${DATABASE_USER}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
      POSTGRES_DB: ${CATALOG_DB_NAME}
    volumes:
      - catalog_db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB} || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 20
    networks:
      - microservices-network

  booking-db:
    image: postgres:15
    container_name: ms-booking-db
    environment:
      POSTGRES_USER: ${DATABASE_USER}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
      POSTGRES_DB: ${BOOKING_DB_NAME}
    volumes:
      - booking_db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB} || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 20
    networks:
      - microservices-network

  redis:
    image: redis:7-alpine
    container_name: ms-redis
    networks:
      - microservices-network

  user-service:
    <<: *ms-common
    container_name: ms-user-service
    command: sh -c "mvn -o -pl user-service spring-boot:run"
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
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/v1/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 180s
    networks:
      - microservices-network

  catalog-service:
    <<: *ms-common
    container_name: ms-catalog-service
    command: sh -c "mvn -o -pl catalog-service spring-boot:run"
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
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/v1/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 180s
    networks:
      - microservices-network

  booking-service:
    <<: *ms-common
    container_name: ms-booking-service
    command: sh -c "mvn -o -pl booking-service spring-boot:run"
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
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/v1/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 180s
    networks:
      - microservices-network

  gateway:
    <<: *ms-common
    container_name: ms-gateway
    command: sh -c "mvn -o -pl gateway spring-boot:run"
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
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 20
      start_period: 60s
    networks:
      - microservices-network

  client:
    build:
      context: ./client
      dockerfile: Dockerfile
    container_name: ms-client
    environment:
      API_URL: http://localhost:8080/api/v1
    ports:
      - "4200:4200"
    volumes:
      - ./client:/app
      - /app/node_modules
      - /app/.angular
    depends_on:
      gateway:
        condition: service_healthy
    networks:
      - microservices-network

networks:
  microservices-network:
    driver: bridge

volumes:
  user_db_data:
  catalog_db_data:
  booking_db_data:
```

> Why these choices (recap): `SERVER_PORT: 8080` makes every service uniform on the network; per-service `DATABASE_URL` points at its own DB container; the booking/user `*_SERVICE_URL` and gateway `*_SERVICE_URI` use container port 8080; the DBs/Redis expose no host ports (avoiding clashes with the running monolith stack); generous `start_period`/`retries` cover the first-run in-container compile.

- [ ] **Step 2: Commit**

```bash
git add docker-compose.microservices.yml
git commit -m "Add docker-compose.microservices.yml: 3 DBs + Redis + 4 services + gateway + client"
```

---

### Task 4: Validate the stack + add a run guide

Automated validation is the reactor build (gateway + all services) plus a Compose config check. The full multi-service `docker compose up` (which builds 4 Spring Boot apps from source in containers and can take 10–20 minutes on first run) is documented as an operator smoke test, not an automated gate.

**Files:**
- Rewrite: `README.md` (translate the existing Romanian content to English, then append the "Running the Microservices Stack" section)

- [ ] **Step 1: Validate the full reactor builds** (confirms the gateway integrates and nothing regressed)

From `microservices/` run: `mvn clean verify`
Expected: `BUILD SUCCESS` for all five modules — common 10, user-service 57, catalog-service 121, booking-service 106, gateway 1 test, all green.

- [ ] **Step 2: Validate the Compose file** (YAML + anchors + interpolation)

From the repo root run: `docker compose -f docker-compose.microservices.yml config -q`
Expected: exit code 0 and no errors. (If it warns that `USER_DB_NAME`/`CATALOG_DB_NAME`/`BOOKING_DB_NAME` are unset, that means the real `.env` doesn't yet have them — add the three lines from `.env.example`; the warning does not fail validation but the actual `up` needs them.)

Also confirm the renderer expands the shared `&ms-common` anchor into each service that uses it:
Run: `docker compose -f docker-compose.microservices.yml config --services | sort`
Expected (8 services): `booking-db`, `booking-service`, `catalog-db`, `catalog-service`, `client`, `gateway`, `redis`, `user-db`, `user-service` — i.e. the 3 application services + `gateway` + `client` + 3 DBs + `redis`. (Confirming the 4 reactor services all rendered with the `./microservices` build: `docker compose ... config | grep -c "context:.*microservices"` returns `4` on most Compose versions, or `5` on versions that also echo the top-level `x-ms-common` extension's `context:` line — both indicate the anchor expanded correctly; the authoritative gate is the `config -q` exit code 0.)

- [ ] **Step 3: Rewrite the README in English (translation + run guide)**

The repo `README.md` is in Romanian. Overwrite it (Write tool) with the **entire** content below — the existing sections translated to English, followed by the new run-guide section:

```markdown
# Web-Applications--Cinema-Booking-Management-Platform

The application is a Cinema Booking & Management System platform that lets users browse available movies, select screenings, and book tickets, and lets administrators manage the platform's content (movies, rooms, schedule).
The system was initially designed as a monolithic application, later to be decomposed into a microservices-based architecture. The split is based on the application's main responsibilities: user management, movie management, and booking management.

# User Management
The system must allow user registration.
The system must allow user authentication.
The system must allow users to log out.
The system must manage roles.
The system must restrict access to certain features based on role.

# Movie Management
The system must allow viewing the list of movies.
The system must allow viewing the details of a movie.
Administrators must be able to: add movies, edit movies, delete movies.
The system must allow associating movies with screenings.
The system must allow searching and sorting movies.

# Screening and Room Management
The system must allow creating screenings for movies.
The system must allow associating a screening with a cinema room.
The system must manage the available seats in a room.
The system must allow viewing the available seats for a screening.

# Booking Management
Users must be able to: select a screening, select available seats, view their own bookings, cancel bookings.
The system must allow creating a booking.
The system must generate tickets for each booked seat.
The system must prevent double-booking of the same seat.

# Running the Microservices Stack (Docker)

The new microservices architecture lives in `microservices/` (a multi-module Maven
reactor) and runs via `docker-compose.microservices.yml`. It is independent of the
original monolith (`docker-compose.yml`) — run one or the other, not both at once
(the gateway and the monolith both use host port 8080).

## Prerequisites

- Docker + Docker Compose v2.
- A `.env` file at the repo root (copy from `.env.example`). It must include the
  three microservices DB-name vars:

  ```
  USER_DB_NAME=user_db
  CATALOG_DB_NAME=catalog_db
  BOOKING_DB_NAME=booking_db
  ```

  All other vars (`DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET_KEY`,
  `TMDB_API_KEY`, `BOOTSTRAP_OWNER_*`, `SECURITY_*`) are shared with the monolith.

## Start

```bash
docker compose -f docker-compose.microservices.yml up --build
```

First run is slow: the shared image compiles the whole reactor (`mvn install`), then
each service container compiles + boots its module. Watch the healthchecks; the
gateway only starts routing once `user-service`, `catalog-service`, and
`booking-service` report healthy.

## Ports

| URL | Component |
|---|---|
| http://localhost:8080/api/v1 | **API gateway** (what the frontend uses) |
| http://localhost:4200 | Angular client |
| http://localhost:8081/api/v1 | user-service (direct, debugging) |
| http://localhost:8082/api/v1 | catalog-service (direct) |
| http://localhost:8083/api/v1 | booking-service (direct) |

Internal `/internal/**` endpoints are **not** routed by the gateway — they are reachable
only over the Docker network (service-to-service Feign calls).

## Smoke test (through the gateway)

```bash
# Register (user-service via gateway). booking-service is up, so the welcome
# notification is delivered via the user->booking Feign call.
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!","confirmPassword":"Password123!","email":"demo@example.com","firstName":"Demo","lastName":"User","phoneNumber":"+1234567890"}'

# Log in (sets jwt + refresh + XSRF-TOKEN cookies)
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!"}'
```

Expect `201` then `200` with `Set-Cookie` headers — proving the gateway routes to
user-service and the cross-service calls work. The gateway logs every request
(`Gateway POST /api/v1/auth/login -> 200 OK (… ms)`).

## Stop

```bash
docker compose -f docker-compose.microservices.yml down        # keep data
docker compose -f docker-compose.microservices.yml down -v     # also drop DB volumes
```
```

- [ ] **Step 4: (Optional, operator) End-to-end smoke run**

This is heavy (first-run in-container compilation of all services). Run it manually when you want to verify the live stack:

```bash
docker compose -f docker-compose.microservices.yml up -d --build
# wait until all services are healthy (can take 10-20 min on first build):
docker compose -f docker-compose.microservices.yml ps
# once healthy, exercise the gateway:
curl -i -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!","confirmPassword":"Password123!","email":"demo@example.com","firstName":"Demo","lastName":"User","phoneNumber":"+1234567890"}'
# tear down:
docker compose -f docker-compose.microservices.yml down
```

Expected: `register` returns `201`; the `ms-gateway` logs show the routed request; the booking-service log shows the inbound `/internal/notifications` call from user-service.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "Translate README to English and document running the microservices stack"
```

---

## Phase 4 Done — What Exists Now

- A reactive **API gateway** (`:8080`) with static routes to all three services, Spring Cloud Gateway's built-in `RequestRateLimiter` + `RedisRateLimiter` per-client rate limiting (`429` over threshold, reusing the shared Redis), and a request/response logging filter — completing requirement area #4.
- **`docker-compose.microservices.yml`** brings up the entire stack — 3 per-service Postgres DBs, shared Redis, `user-service`/`catalog-service`/`booking-service`, the gateway, and the Angular client — with one command, on the same `http://localhost:8080/api/v1` the frontend already targets (no frontend changes).
- Per-service databases (enabled by Phase 3's denormalization), Actuator `/actuator/health` healthchecks on every service, and a shared run-from-source image with baked dependencies.
- The full 5-module reactor builds green (gateway + the three services + common).

**The microservices extraction is complete** across the four phases: `common` + `user-service` (Phase 1), `catalog-service` (Phase 2), `booking-service` + the 3-way Feign graph + denormalization (Phase 3), and the gateway + Docker Compose orchestration (Phase 4). This establishes the foundation for the remaining coursework requirement areas (centralized config, service discovery/load balancing, Prometheus/Grafana/tracing, deeper resilience, Saga/CQRS docs, MongoDB, micro-frontends, CI/CD, AI agents), each of which builds on this running multi-service stack in its own future phase.

