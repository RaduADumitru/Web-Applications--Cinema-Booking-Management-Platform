# Microservices Phase 2: `catalog-service` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fully working `catalog-service` module to the `microservices/` reactor — the cinema catalog (movies, rooms, seats, screen sessions) with its TMDB integration and Redis-backed caching, ported from the monolith — plus the internal `GET /internal/ticket-setup` endpoint (server side of Feign integration ①) that a future booking-service will call to validate and snapshot ticket data.

**Architecture:** A third reactor module, `catalog-service` (Spring Boot app, host port 8082), depending on the existing `common` library. The 7 catalog entities and all their repositories/services/controllers/DTOs move over **verbatim** (no cross-references to `User`/`Order`/`Ticket`/`Notification` exist), reusing base package `com.awbd.cinema` so `common`'s beans (stateless JWT filter, role hierarchy, exceptions, `RestPage`, CORS/CSRF) are component-scanned. The only real new code is a catalog-specific `SecurityConfig` (no login beans — catalog only validates JWTs), the `/internal/ticket-setup` endpoint, and a shared `TicketSetupDTO` added to `common`. Caching is Redis-backed (Jedis), exactly as in the monolith, suppressed under the `test` profile.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2 (transitively via `common`), Java 21, Spring Data JPA, Spring Security, PostgreSQL (existing `cinema` DB, reused for Phase 2), Redis/Jedis caching, `themoviedbapi` 2.6.0 (TMDB), jjwt 0.13.0, Lombok, dotenv-java 3.2.0, JUnit 5 / Mockito / AssertJ / H2.

---

## Background & Key Design Decisions

These apply across all tasks. They were established by reading the monolith source and the design spec (`docs/superpowers/specs/2026-06-14-microservices-extraction-design.md`, §5.2, §6.3①, §7.2, §8.4).

1. **Everything reuses package `com.awbd.cinema`.** All ported catalog files keep their exact packages, so plain `Copy-Item` works (no import rewrites) and the `catalog-service` `@SpringBootApplication` (at `com.awbd.cinema`) component-scans both its own code and `common`'s beans.

2. **Catalog is a pure JWT *validator*, not an issuer.** It performs no login. Its `SecurityConfig` is a **new, slimmer** version than user-service's: it keeps CORS, CSRF (cookie-based, validating the `XSRF-TOKEN` set by user-service at login), the stateless `JwtAuthenticationFilter` (from `common`), `@EnableMethodSecurity`, and the `/internal/**` + `OPTIONS` permitAll rules — but it **drops** every login-only bean (`AuthenticationManager`, `AuthenticationProvider`, `BCryptPasswordEncoder`, `UserDetailsService`, `LoginAttemptService`). All catalog write endpoints use `@PreAuthorize("hasRole('STAFF')")`; all reads require an authenticated user (`anyRequest().authenticated()`), preserving exact monolith behaviour.

3. **`TicketSetupDTO` lives in `common`** (shared by the catalog producer now and the booking consumer in Phase 3). Its `seatZone` is a `String` (the `SeatZone` enum's `name()`), so `common` need not depend on the catalog-only `SeatZone` enum, and the JSON is identical (enums serialize as their name anyway).

4. **`/internal/ticket-setup` reproduces the monolith's `TicketServiceImpl.createTicket` validation.** It loads `Seat`/`Room`/`ScreenSession`, runs the two relationship checks (`roomRepository.existsByIdAndSeatsId`, `existsByIdAndScreenSessionsId`), and returns a pricing/display snapshot. The "ticket already exists" check stays in booking-service (its own table, Phase 3). The method is `@Transactional(readOnly = true)` so Hibernate can lazily load `seat.category`, `session.movie`, `session.sessionInfo`.

5. **`CacheProperties` moves into `common`** (`@ConfigurationProperties("app.cache")`, a pure POJO with no Redis dependency — safe for user-service to scan harmlessly). **`RedisConfig` stays in `catalog-service`** — it must NOT go in `common`, because it auto-activates (`@Profile("!test")`, `@EnableCaching`) and would try to open a Redis connection inside user-service (which has no Redis). Booking-service will get its own copy in Phase 3.

6. **Phase 2 reuses the existing local Postgres** (`cinema` DB, `localhost:5432`) via the same env vars as Phase 1 — the catalog tables (`movies`, `rooms`, `seats`, `screen_sessions`, …) already exist there. Per-service DB containers are deferred to Phase 4. `ddl-auto=update` is additive and safe.

7. **Redis caching is suppressed under the `test` profile** exactly as in the monolith: `RedisConfig` is `@Profile("!test")`, so under tests there is no `@EnableCaching` and `@Cacheable`/`@CacheEvict` annotations become no-ops (no `CacheManager` needed). The H2 `application-test.yml` profile (mirroring user-service Phase 1 and the monolith) keeps the whole test suite self-contained — note `NON_KEYWORDS=ROW,...` is required because `Seat` has a column literally named `row`.

8. **No outgoing Feign from catalog in Phase 2.** Catalog is only the *callee* of integration ①. It needs no `@EnableFeignClients` and no Feign client classes. (`common` transitively puts the OpenFeign/Resilience4j starters on the classpath; they're simply unused here.)

---

### Task 1: `common` — `CacheProperties` + `TicketSetupDTO`

Add the two shared pieces catalog needs from `common`: relocate `CacheProperties` (so both catalog now and booking later share it) and add the `TicketSetupDTO` Feign contract.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/utils/CacheProperties.java`
- Create: `microservices/common/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/TicketSetupDTO.java`

- [ ] **Step 1: Add `CacheProperties` to `common`** (byte-identical to the monolith's `cinema/src/main/java/com/awbd/cinema/utils/CacheProperties.java`)

Create `microservices/common/src/main/java/com/awbd/cinema/utils/CacheProperties.java`:

```java
package com.awbd.cinema.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    private Duration defaultTtl = Duration.ofMinutes(5);
    private Map<String, Duration> caches = new HashMap<>();
}
```

- [ ] **Step 2: Add `TicketSetupDTO` to `common`**

Create `microservices/common/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/TicketSetupDTO.java`:

```java
package com.awbd.cinema.DTOs.TicketDTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TicketSetupDTO(
        Integer seatRow,
        Integer seatNumber,
        String seatZone,
        BigDecimal extraFee,
        Integer extraPoints,
        String movieTitle,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        Integer sessionPoints
) {
}
```

- [ ] **Step 3: Verify `common` still builds**

From `microservices/` run: `mvn -pl common verify`
Expected: `BUILD SUCCESS`, all existing `common` tests still green (10 tests).

(Maven 3.9.11 + Java 21 are on PATH as `mvn`; there is no `mvnw` wrapper inside `microservices/`.)

- [ ] **Step 4: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/utils/CacheProperties.java microservices/common/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/TicketSetupDTO.java
git commit -m "Add CacheProperties and TicketSetupDTO to common for catalog-service"
```

---

### Task 2: `catalog-service` skeleton — POM, app, properties, enums, entities, repositories

Register the module and lay down the data layer (which compiles on its own against `common` + JPA).

**Files:**
- Modify: `microservices/pom.xml` (add `<module>catalog-service</module>`)
- Create: `microservices/catalog-service/pom.xml`
- Create: `microservices/catalog-service/src/main/java/com/awbd/cinema/CatalogServiceApplication.java`
- Create: `microservices/catalog-service/src/main/resources/application.properties`
- Create: `microservices/catalog-service/src/main/resources/application.yml`
- Create: `microservices/catalog-service/src/main/resources/application-test.yml`
- Copy (verbatim): 5 enums, 7 entities, 7 repositories

- [ ] **Step 1: Add the module to the reactor**

In `microservices/pom.xml`, change:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
    </modules>
```

to:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
    </modules>
```

- [ ] **Step 2: Create `microservices/catalog-service/pom.xml`**

Like user-service's pom, but with TMDB (`themoviedbapi`) + Redis (`spring-boot-starter-data-redis` + `jedis`) and **no** Caffeine (no login-attempt cache here).

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

    <artifactId>catalog-service</artifactId>
    <packaging>jar</packaging>
    <name>catalog-service</name>
    <description>Movie/room/seat/screen-session catalog microservice</description>

    <dependencies>
        <dependency>
            <groupId>com.awbd</groupId>
            <artifactId>common</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>redis.clients</groupId>
            <artifactId>jedis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>uk.co.conoregan</groupId>
            <artifactId>themoviedbapi</artifactId>
            <version>2.6.0</version>
        </dependency>

        <dependency>
            <groupId>io.github.cdimascio</groupId>
            <artifactId>dotenv-java</artifactId>
            <version>3.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.13.0</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.13.0</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.13.0</version>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create `CatalogServiceApplication`**

`@EnableSpringDataWebSupport(VIA_DTO)` is required because the catalog controllers return paginated `RestPage` bodies. No `@EnableFeignClients` (no outgoing Feign), no `@EnableScheduling` (no jobs).

Create `microservices/catalog-service/src/main/java/com/awbd/cinema/CatalogServiceApplication.java`:

```java
package com.awbd.cinema;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class CatalogServiceApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `microservices/catalog-service/src/main/resources/application.properties`**

```properties
spring.application.name=catalog-service

server.port=8082
server.servlet.context-path=/api/v1

logging.file.name=logs/catalog-service.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.total-size-cap=100MB
logging.threshold.file=ERROR

spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update

spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}

jwt.secret.key=${JWT_SECRET_KEY}

auth.cookie.secure=${SECURITY_COOKIE_SECURE}
auth.cookie.same-site=${SECURITY_COOKIE_SAME_SITE}

security.csrf.enabled=${SECURITY_CSRF_ENABLED}
security.website.domain=${SECURITY_WEBSITE_DOMAIN}
security.cors.allowed-origins=${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

tmdb.api.key=${TMDB_API_KEY}

spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

management.endpoints.web.exposure.include=health,info,metrics,prometheus

logging.level.com.awbd.cinema=DEBUG
```

- [ ] **Step 5: Create `microservices/catalog-service/src/main/resources/application.yml`**

The catalog subset of the monolith's `app.cache.caches` map (movie/room/seat/session caches only).

```yaml
app:
  cache:
    default-ttl: 5m
    caches:
      admin_movies: 15m
      public_movie_lists: 10m
      single_movies: 2h
      seat_lists: 10m
      single_seat: 30m
      screen_session_lists: 10m
      movie_session_lists: 10m
      single_screen_sessions: 10m
      room_lists: 15m
      single_room: 30m
```

- [ ] **Step 6: Create `microservices/catalog-service/src/main/resources/application-test.yml`**

H2 in-memory profile for `@ActiveProfiles("test")` (used by `MovieIntegrationTest`). `RedisConfig` is `@Profile("!test")` so it is excluded here — no Redis needed. `tmdb.api.key` is a dummy (the integration test mocks `TmdbApi`, but the `TmdbConfig` `@Configuration` bean still needs the `@Value` to resolve). `NON_KEYWORDS=ROW,...` is required because `Seat` has a column named `row`.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;NON_KEYWORDS=ROW,DAY,VALUE,KEY,YEAR
    driverClassName: org.h2.Driver
    username: sa
    password: password
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  h2:
    console:
      enabled: true

jwt:
  secret:
    key: test-jwt-secret-key-which-is-long-enough-for-hmac-sha-algorithms-1234567890

auth:
  cookie:
    secure: false
    same-site: Lax

security:
  csrf:
    enabled: true
  website:
    domain: localhost
  cors:
    allowed-origins: http://localhost:4200

tmdb:
  api:
    key: test-tmdb-api-key
```

- [ ] **Step 7: Port the 5 enums, 7 entities, and 7 repositories verbatim**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/catalog-service/src/main/java/com/awbd/cinema/enums, microservices/catalog-service/src/main/java/com/awbd/cinema/entities, microservices/catalog-service/src/main/java/com/awbd/cinema/repositories

Copy-Item cinema/src/main/java/com/awbd/cinema/enums/GenreType.java microservices/catalog-service/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/Format.java microservices/catalog-service/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/RoomType.java microservices/catalog-service/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/SeatCategoryType.java microservices/catalog-service/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/SeatZone.java microservices/catalog-service/src/main/java/com/awbd/cinema/enums/

Copy-Item cinema/src/main/java/com/awbd/cinema/entities/Genre.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/Movie.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/Room.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/Seat.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/SeatCategory.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/ScreenSession.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/SessionInfo.java microservices/catalog-service/src/main/java/com/awbd/cinema/entities/

Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/GenreRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/MovieRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/RoomRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/SeatRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/SeatCategoryRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/ScreenSessionRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/SessionInfoRepository.java microservices/catalog-service/src/main/java/com/awbd/cinema/repositories/
```

- [ ] **Step 8: Verify the data layer compiles**

From `microservices/` run: `mvn -pl catalog-service -am compile`
Expected: `BUILD SUCCESS` (`-am` builds `common` first). Entities/repositories/enums + the app class compile against `common` + JPA. (No services/controllers/security yet — that's fine for a compile check.)

- [ ] **Step 9: Commit**

```bash
git add microservices/pom.xml microservices/catalog-service
git commit -m "Scaffold catalog-service module: pom, app, properties, ported enums/entities/repositories"
```

---

### Task 3: `catalog-service` — DTOs, services, controllers (verbatim ports)

Port the business layer. None of these reference `User`/`Order`/`Ticket`/`Notification`; they use `RestPage`/exceptions/`GlobalExceptionHandler` from `common` (same packages), so they port unchanged.

**Files:**
- Copy (verbatim): 9 DTOs (3 Movie, 2 Room, 2 Seat, 2 ScreenSession), 8 service files (4 interfaces + 4 impls), 4 controllers.

- [ ] **Step 1: Port the DTOs, services, and controllers verbatim**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/MovieDTOs, microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/RoomDTOs, microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/SeatDTOs, microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/ScreenSessionDTOs, microservices/catalog-service/src/main/java/com/awbd/cinema/services/MovieService, microservices/catalog-service/src/main/java/com/awbd/cinema/services/RoomService, microservices/catalog-service/src/main/java/com/awbd/cinema/services/SeatService, microservices/catalog-service/src/main/java/com/awbd/cinema/services/ScreenSessionService, microservices/catalog-service/src/main/java/com/awbd/cinema/controllers

Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/MovieDTOs/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/MovieDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/RoomDTOs/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/RoomDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/SeatDTOs/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/SeatDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/ScreenSessionDTOs/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs/ScreenSessionDTOs/

Copy-Item cinema/src/main/java/com/awbd/cinema/services/MovieService/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/services/MovieService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/RoomService/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/services/RoomService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/SeatService/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/services/SeatService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/ScreenSessionService/*.java microservices/catalog-service/src/main/java/com/awbd/cinema/services/ScreenSessionService/

Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/MovieController.java microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/RoomController.java microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/SeatController.java microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/ScreenSessionController.java microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/
```

This copies exactly: `MovieDTOs/{MovieDTO,AdminMovieDTO,SaveMovieDTO}.java`, `RoomDTOs/{RoomDTO,SaveRoomDTO}.java`, `SeatDTOs/{SeatDTO,SaveSeatDTO}.java`, `ScreenSessionDTOs/{ScreenSessionDTO,SaveScreenSessionDTO}.java`; `MovieService/{MovieService,MovieServiceImpl}.java`, `RoomService/{RoomService,RoomServiceImpl}.java`, `SeatService/{SeatService,SeatServiceImpl}.java`, `ScreenSessionService/{ScreenSessionService,ScreenSessionServiceImpl}.java`; and the 4 controllers.

> `MovieServiceImpl` injects `TmdbApi` and `AdminMovieDTO` references the TMDB SDK type — both resolve via the `themoviedbapi` dependency added in Task 2. The `@Cacheable`/`@CacheEvict` annotations reference cache names as plain strings and compile without any cache config present.

- [ ] **Step 2: Verify the business layer compiles**

From `microservices/` run: `mvn -pl catalog-service -am compile`
Expected: `BUILD SUCCESS`. Controllers compile against the now-present services; services compile against repositories/entities/DTOs/`TmdbApi`. (Beans like `TmdbApi`/`CacheManager`/`SecurityConfig` are runtime concerns, added in Task 4 — compilation does not need them.)

- [ ] **Step 3: Commit**

```bash
git add microservices/catalog-service/src/main/java/com/awbd/cinema/DTOs microservices/catalog-service/src/main/java/com/awbd/cinema/services microservices/catalog-service/src/main/java/com/awbd/cinema/controllers
git commit -m "Port catalog DTOs, services, and controllers to catalog-service"
```

---

### Task 4: `catalog-service` — TMDB config, Redis config, and SecurityConfig

Add the configuration beans. `TmdbConfig` and `RedisConfig` port verbatim from the monolith (`RedisConfig` keeps `@Profile("!test")`). `SecurityConfig` is **new** — the slim, validator-only variant (no login beans).

**Files:**
- Copy (verbatim): `TmdbConfig`, `RedisConfig`
- Create (new): `microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`

- [ ] **Step 1: Port `TmdbConfig` and `RedisConfig` verbatim**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/catalog-service/src/main/java/com/awbd/cinema/config, microservices/catalog-service/src/main/java/com/awbd/cinema/security

Copy-Item cinema/src/main/java/com/awbd/cinema/config/TmdbConfig.java microservices/catalog-service/src/main/java/com/awbd/cinema/config/
Copy-Item cinema/src/main/java/com/awbd/cinema/config/RedisConfig.java microservices/catalog-service/src/main/java/com/awbd/cinema/config/
```

> `RedisConfig` imports `com.awbd.cinema.utils.CacheProperties`, which now resolves from the `common` jar (added in Task 1). No change needed.

- [ ] **Step 2: Create the catalog `SecurityConfig`**

Create `microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`. This is the user-service `SecurityConfig` minus all login beans (`UserDetailsService`, `LoginAttemptService`, `AuthenticationManager`, `AuthenticationProvider`, `BCryptPasswordEncoder`), with `/internal/**` permitAll + CSRF-exempt, and `anyRequest().authenticated()` (catalog has no `/auth/**`).

```java
package com.awbd.cinema.security;

import com.awbd.cinema.exceptions.UnauthenticatedException;
import com.awbd.cinema.utils.SecurityCorsProperties;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityCorsProperties securityCorsProperties;

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Value("${security.website.domain}")
    private String websiteDomain;

    @Value("${security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Value("${auth.cookie.secure:false}")
    private boolean authCookieSecure;
    @Value("${auth.cookie.same-site:Lax}")
    private String authCookieSameSite;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            handlerExceptionResolver.resolveException(request, response, null,
                                    new UnauthenticatedException("Authentication required."));
                        })
                )
                .authorizeHttpRequests(authz -> authz
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);

        if (csrfEnabled) {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(cookieCsrfTokenRepository())
                    .csrfTokenRequestHandler(requestHandler)
                    .ignoringRequestMatchers("/internal/**")
            );
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        List<String> allowedOrigins = securityCorsProperties.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of(websiteDomain);
        }
        corsConfiguration.setAllowedOriginPatterns(allowedOrigins);
        corsConfiguration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN"
        ));
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(authCookieSecure);
            cookie.sameSite(authCookieSameSite);
        });
        return repository;
    }
}
```

> `JwtAuthenticationFilter` and `CsrfCookieFilter` resolve from `common` (same package `com.awbd.cinema.security`). The role-hierarchy beans come from `common`'s `RoleHierarchyConfig` (component-scanned), so `@PreAuthorize("hasRole('STAFF')")` works.

- [ ] **Step 3: Verify the whole main source compiles**

From `microservices/` run: `mvn -pl catalog-service -am compile`
Expected: `BUILD SUCCESS`. The full catalog main tree now compiles.

- [ ] **Step 4: Commit**

```bash
git add microservices/catalog-service/src/main/java/com/awbd/cinema/config microservices/catalog-service/src/main/java/com/awbd/cinema/security
git commit -m "Add catalog-service config (TMDB, Redis) and validator-only SecurityConfig"
```

---

### Task 5: `catalog-service` — internal `/ticket-setup` endpoint (Feign integration ① server side)

Add the endpoint a future booking-service calls at ticket-creation time. It reproduces the monolith `TicketServiceImpl.createTicket` relationship validation (seat-in-room, session-in-room) and returns a `TicketSetupDTO` pricing/display snapshot. TDD: write the service test first.

**Files:**
- Create: `microservices/catalog-service/src/main/java/com/awbd/cinema/services/TicketSetupService/TicketSetupService.java`
- Create: `microservices/catalog-service/src/main/java/com/awbd/cinema/services/TicketSetupService/TicketSetupServiceImpl.java`
- Create: `microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/InternalCatalogController.java`
- Test: `microservices/catalog-service/src/test/java/com/awbd/cinema/services/TicketSetupServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `microservices/catalog-service/src/test/java/com/awbd/cinema/services/TicketSetupServiceTest.java`:

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.entities.SessionInfo;
import com.awbd.cinema.enums.SeatZone;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.services.TicketSetupService.TicketSetupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSetupServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ScreenSessionRepository screenSessionRepository;

    @InjectMocks private TicketSetupServiceImpl ticketSetupService;

    private Seat seat;
    private Room room;
    private ScreenSession session;

    @BeforeEach
    void setUp() {
        SeatCategory category = SeatCategory.builder()
                .extraFee(new BigDecimal("5.00"))
                .extraPoints(3)
                .build();
        seat = Seat.builder()
                .id(1L).row(4).number(7).zone(SeatZone.VIP).category(category)
                .build();
        room = Room.builder().id(2L).build();
        Movie movie = Movie.builder().id(10L).title("Inception").build();
        SessionInfo sessionInfo = SessionInfo.builder().points(8).build();
        session = ScreenSession.builder()
                .id(3L).date(LocalDate.of(2026, 7, 1)).startTime(LocalTime.of(19, 30))
                .movie(movie).sessionInfo(sessionInfo)
                .build();
    }

    @Test
    void getTicketSetup_ReturnsSnapshot_WhenValid() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);

        TicketSetupDTO dto = ticketSetupService.getTicketSetup(1L, 2L, 3L);

        assertEquals(4, dto.seatRow());
        assertEquals(7, dto.seatNumber());
        assertEquals("VIP", dto.seatZone());
        assertEquals(new BigDecimal("5.00"), dto.extraFee());
        assertEquals(3, dto.extraPoints());
        assertEquals("Inception", dto.movieTitle());
        assertEquals(LocalDate.of(2026, 7, 1), dto.sessionDate());
        assertEquals(LocalTime.of(19, 30), dto.sessionStartTime());
        assertEquals(8, dto.sessionPoints());
    }

    @Test
    void getTicketSetup_DefaultsFeeAndPoints_WhenNoCategoryOrSessionInfo() {
        seat.setCategory(null);
        session.setSessionInfo(null);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);

        TicketSetupDTO dto = ticketSetupService.getTicketSetup(1L, 2L, 3L);

        assertEquals(BigDecimal.ZERO, dto.extraFee());
        assertEquals(0, dto.extraPoints());
        assertEquals(0, dto.sessionPoints());
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenSeatMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenRoomMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenSessionMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsBadRequest_WhenSeatNotInRoom() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsBadRequest_WhenSessionNotInRoom() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (no implementation yet)**

From `microservices/` run: `mvn -pl catalog-service -am test -Dtest=TicketSetupServiceTest`
Expected: FAIL — compilation error, `TicketSetupServiceImpl`/`TicketSetupService` do not exist yet.

- [ ] **Step 3: Create the service interface**

Create `microservices/catalog-service/src/main/java/com/awbd/cinema/services/TicketSetupService/TicketSetupService.java`:

```java
package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;

public interface TicketSetupService {
    TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId);
}
```

- [ ] **Step 4: Create the service implementation**

Create `microservices/catalog-service/src/main/java/com/awbd/cinema/services/TicketSetupService/TicketSetupServiceImpl.java`:

```java
package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TicketSetupServiceImpl implements TicketSetupService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final ScreenSessionRepository screenSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));

        if (!roomRepository.existsByIdAndSeatsId(room.getId(), seat.getId())) {
            throw new BadRequestException("Seat does not belong to the specified room.");
        }
        if (!roomRepository.existsByIdAndScreenSessionsId(room.getId(), session.getId())) {
            throw new BadRequestException("Screen session is not scheduled in the specified room.");
        }

        BigDecimal extraFee = seat.getCategory() != null ? seat.getCategory().getExtraFee() : BigDecimal.ZERO;
        Integer extraPoints = seat.getCategory() != null ? seat.getCategory().getExtraPoints() : 0;
        Integer sessionPoints = session.getSessionInfo() != null ? session.getSessionInfo().getPoints() : 0;

        return new TicketSetupDTO(
                seat.getRow(),
                seat.getNumber(),
                seat.getZone().name(),
                extraFee,
                extraPoints,
                session.getMovie().getTitle(),
                session.getDate(),
                session.getStartTime(),
                sessionPoints
        );
    }
}
```

- [ ] **Step 5: Create the internal controller**

Create `microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/InternalCatalogController.java`:

```java
package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.services.TicketSetupService.TicketSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCatalogController {

    private final TicketSetupService ticketSetupService;

    @GetMapping("/ticket-setup")
    public ResponseEntity<TicketSetupDTO> ticketSetup(
            @RequestParam Long seatId,
            @RequestParam Long roomId,
            @RequestParam Long sessionId) {
        return ResponseEntity.ok(ticketSetupService.getTicketSetup(seatId, roomId, sessionId));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

From `microservices/` run: `mvn -pl catalog-service -am test -Dtest=TicketSetupServiceTest`
Expected: PASS (7 tests).

- [ ] **Step 7: Commit**

```bash
git add microservices/catalog-service/src/main/java/com/awbd/cinema/services/TicketSetupService microservices/catalog-service/src/main/java/com/awbd/cinema/controllers/InternalCatalogController.java microservices/catalog-service/src/test/java/com/awbd/cinema/services/TicketSetupServiceTest.java
git commit -m "Add catalog-service internal ticket-setup endpoint (Feign integration 1 server side)"
```

---

### Task 6: `catalog-service` — port/adapt tests + full reactor build

Port the catalog test suite (service tests, controller tests, config tests, the movie integration test) and adapt the one piece that changed (`BaseControllerTest`). Then build the whole 3-module reactor green.

**Files:**
- Create: `microservices/catalog-service/src/test/resources/application.properties` (default-profile literals for the `@WebMvcTest` controller tests)
- Copy (verbatim): `MovieServiceTest`, `RoomServiceTest`, `SeatServiceTest`, `ScreenSessionServiceTest`, `MovieControllerTest`, `RoomControllerTest`, `SeatControllerTest`, `ScreenSessionControllerTest`, `RedisConfigTest`, `TmdbConfigTest`, `MovieIntegrationTest`
- Create (adapted): `microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java`

- [ ] **Step 1: Create the default-profile test properties**

The `@WebMvcTest` controller tests run under the **default** profile (no `@ActiveProfiles("test")`), so they do not load `application-test.yml`. The imported `SecurityConfig` injects `@Value("${security.website.domain}")` (no default), so that property must exist for the web-slice context to start. Provide literal test values here (mirrors user-service Phase 1). `@WebMvcTest` does not autoconfigure a `DataSource`/JPA, so no datasource props are needed; `MovieIntegrationTest` (`@ActiveProfiles("test")`) overrides what it needs from `application-test.yml`.

Create `microservices/catalog-service/src/test/resources/application.properties`:

```properties
spring.application.name=catalog-service
server.servlet.context-path=/api/v1

jwt.secret.key=redacted

auth.cookie.secure=false
auth.cookie.same-site=Lax

security.csrf.enabled=true
security.website.domain=http://localhost:4200
security.cors.allowed-origins=${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

tmdb.api.key=test-tmdb-api-key

spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

- [ ] **Step 2: Create the adapted `BaseControllerTest`**

The catalog controller tests use only `loginAs(...)` / `loginAsDefaultUser()` plus their own `@MockitoBean` service — they never reference the base's old `userDetailsService`/`loginAttemptService`/`jwtUtil` fields (verified). So the catalog base mocks **only** what catalog's `SecurityConfig` injects: `JwtAuthenticationFilter` + `SecurityCorsProperties`. It imports catalog's `SecurityConfig` + `common`'s `RoleHierarchyConfig`, and builds `CustomUserDetails` via the primitive constructor.

Create `microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java`:

```java
package com.awbd.cinema.controllers;

import com.awbd.cinema.config.RoleHierarchyConfig;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.security.JwtAuthenticationFilter;
import com.awbd.cinema.security.SecurityConfig;
import com.awbd.cinema.utils.SecurityCorsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest
@Import({SecurityConfig.class, RoleHierarchyConfig.class})
public abstract class BaseControllerTest {

    @MockitoBean
    protected JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    protected SecurityCorsProperties securityCorsProperties;

    @BeforeEach
    void setupMockFilters() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected CustomUserDetails loginAsDefaultUser() {
        return loginAs(1L, "test_user", Role.USER);
    }

    protected CustomUserDetails loginAs(Long userId, String username, Role role) {
        CustomUserDetails userDetails =
                new CustomUserDetails(userId, username, "password123", role, null);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        return userDetails;
    }
}
```

- [ ] **Step 3: Port the verbatim test files**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/catalog-service/src/test/java/com/awbd/cinema/services, microservices/catalog-service/src/test/java/com/awbd/cinema/controllers, microservices/catalog-service/src/test/java/com/awbd/cinema/config

Copy-Item cinema/src/test/java/com/awbd/cinema/services/MovieServiceTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/RoomServiceTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/SeatServiceTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/ScreenSessionServiceTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/MovieControllerTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/RoomControllerTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/SeatControllerTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/ScreenSessionControllerTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/config/RedisConfigTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/config/
Copy-Item cinema/src/test/java/com/awbd/cinema/config/TmdbConfigTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/config/
Copy-Item cinema/src/test/java/com/awbd/cinema/MovieIntegrationTest.java microservices/catalog-service/src/test/java/com/awbd/cinema/
```

> Confirmed unchanged: the 4 service tests are Mockito unit tests; the 4 controller tests extend `BaseControllerTest` and only call `loginAs`/`loginAsDefaultUser`; `RedisConfigTest` and `TmdbConfigTest` are pure unit tests (they `new` the config classes directly) and reference `CacheProperties`/`SecurityCorsProperties`, which resolve from `common`; `MovieIntegrationTest` runs `@SpringBootTest @ActiveProfiles("test")` on H2 with a mocked `TmdbApi`/`TmdbMovies` and uses Spring Security's `user()`/`csrf()` test post-processors (no real JWT), so it is self-contained.

- [ ] **Step 4: Build the entire reactor (self-contained — no external DB or Redis)**

`MovieIntegrationTest` runs on the H2 `test` profile (Redis suppressed via `@Profile("!test")`), so the whole reactor builds without any external service. From `microservices/` run:

`mvn clean verify`

Expected: `BUILD SUCCESS` for all three modules. Test totals — `common`: 10; `user-service`: 57; `catalog-service`: the 4 service tests + 4 controller tests + `RedisConfigTest` (5) + `TmdbConfigTest` (1) + `TicketSetupServiceTest` (7) + `MovieIntegrationTest` (1), all green.

(Maven 3.9.11 + Java 21 on PATH as `mvn`; no `mvnw` inside `microservices/`.)

- [ ] **Step 5: Commit**

```bash
git add microservices/catalog-service/src/test
git commit -m "Port and adapt catalog-service tests; full reactor builds green"
```

---

## Phase 2 Done — What Exists Now

- `microservices/` reactor now has three runnable services: `common`, `user-service` (`:8081`), `catalog-service` (`:8082`).
- Full catalog (movies/rooms/seats/screen-sessions) ported with TMDB integration and Redis-backed caching intact, behind the shared stateless-JWT security layer.
- `GET /internal/ticket-setup` ready as the server side of Feign integration ①, plus the shared `TicketSetupDTO` in `common`.
- Whole ported + new test suite green on H2 (no external DB/Redis needed for the build).

**Deferred to later phases (not gaps):** `booking-service` + the booking-side of all three Feign integrations + `Ticket`/`Order`/`Notification` entity denormalization + `NotificationScheduler` rewrite (Phase 3); API gateway + `docker-compose.microservices.yml` + per-service Postgres/Redis containers + Actuator-based healthchecks (Phase 4).

