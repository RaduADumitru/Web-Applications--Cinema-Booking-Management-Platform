# Microservices Phase 3: `booking-service` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the final `booking-service` module (orders, tickets, ticket-info, offers, points-spend, notifications + the movie-reminder scheduler) to the `microservices/` reactor, completing the 3-way Feign communication graph — booking→catalog (ticket setup), booking↔user (loyalty points), and user→booking (registration notification, server side) — with entity denormalization that removes every cross-service JPA join.

**Architecture:** A fourth reactor module, `booking-service` (Spring Boot app, host port 8083), depending on `common`. `TicketInfo`/`Offer`/`PointsSpend` and most DTOs/controllers/services port verbatim. `Order`/`Notification` drop their `@ManyToOne User` for a plain `Long userId`; `Ticket` drops its `@ManyToOne Seat/Room/ScreenSession` for plain `Long` IDs **plus** denormalized snapshot columns (seat row/number/zone, extra fee/points, movie title, session date/time/points) populated once at ticket creation from a Feign call to catalog-service's `/internal/ticket-setup`. `OrderServiceImpl` reads all pricing from those local snapshot fields (zero catalog calls on the hot path) and uses a Feign `UserServiceClient` for loyalty points; both Feign clients have Resilience4j circuit breakers + fallbacks. The scheduler is rewritten to be fully booking-local using the snapshot fields. A `BookingServiceApplication` enables Feign + scheduling.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2 (OpenFeign + Resilience4j), Java 21, Spring Data JPA, Spring Security, PostgreSQL (existing `cinema` DB, reused), Redis/Jedis caching, jjwt 0.13.0, Lombok, dotenv-java 3.2.0, JUnit 5 / Mockito / AssertJ / H2.

---

## Background & Key Design Decisions

Established by reading the monolith source + design spec (`docs/superpowers/specs/2026-06-14-microservices-extraction-design.md` §5.3, §6.3①②③, §6.4, §7.1, §7.2, §7.3, §8.4).

1. **Package reuse + component scanning** as in Phases 1–2: every file keeps package `com.awbd.cinema`; `BookingServiceApplication` scans booking code + `common` beans (stateless JWT filter, `RoleHierarchyConfig`, exceptions, `RestPage`, `CacheProperties`, the shared Feign DTOs `TicketSetupDTO`/`LoyaltyPointsDTO`/`AdjustLoyaltyPointsDTO`/`CreateNotificationDTO`).

2. **Validator-only `SecurityConfig`** — identical shape to catalog-service's (no login beans; `/internal/**` permitAll + CSRF-exempt; `anyRequest().authenticated()`; `@PreAuthorize("hasRole('STAFF')")` on writes).

3. **Entity denormalization (§7.1/§7.2):**
   - `Order.user` (`@ManyToOne User`) → `Order.userId` (`Long`, column `user_id`). `OrderDTO.from`: `o.getUser().getId()` → `o.getUserId()`. **JSON unchanged.**
   - `Notification.user` (`@ManyToOne User`) → `Notification.userId` (`Long`, column `user_id`); `Notification.order` stays a real FK. `NotificationDTO.from`: `n.getUser().getId()` → `n.getUserId()`. **JSON unchanged.**
   - `Ticket.seat`/`.room`/`.screenSession` → plain `Long seatId`/`roomId`/`screenSessionId` (columns `seat_id`/`room_id`/`session_id`, unique constraint preserved) **plus** snapshot columns `seatRow`, `seatNumber`, `seatZone` (String), `extraFee` (BigDecimal), `extraPoints` (Integer), `movieTitle` (String), `sessionDate` (LocalDate), `sessionStartTime` (LocalTime), `sessionPoints` (Integer). `Ticket.order`/`Ticket.ticketInfo` stay FKs. `TicketDTO.from`: `t.getSeat().getId()` → `t.getSeatId()` etc. **JSON unchanged.** These snapshot fields are exactly the components of the shared `TicketSetupDTO` (common, from Phase 1/2).
   - Spring Data derived queries (`findByUserId`, `existsBySeatIdAndRoomIdAndScreenSessionId`, `findByScreenSessionId…`) resolve against the **renamed plain fields**, so no `@Query` rewrites are needed.

4. **Feign integration ① (booking→catalog, §6.3①):** `TicketServiceImpl.createTicket` no longer loads `Seat`/`Room`/`ScreenSession` via JPA. It calls `catalogServiceClient.getTicketSetup(seatId, roomId, sessionId)` (which performs the seat-in-room / session-in-room validation in catalog and returns the snapshot), does the booking-local "ticket already exists" check, then builds the `Ticket` from plain IDs + the snapshot. `@FeignClient(fallback=…)` → on catalog failure the fallback throws a clear "catalog service unavailable" error (admin-only write; no degraded mode).

5. **Feign integration ② (booking↔user, §6.3②):** `OrderServiceImpl` reads/writes loyalty points via `userServiceClient.getLoyaltyPoints(userId)` / `updateLoyaltyPoints(userId, AdjustLoyaltyPointsDTO)`. Fallback: GET returns `LoyaltyPointsDTO(userId, 0)` (→ no discount applied) and PATCH is a logged no-op (→ points not awarded), so order creation/payment never fails because user-service is down — the spec'd PoC degradation and the flagged future-Saga candidate.

6. **Feign integration ③ (user→booking, §6.3③) — server side:** booking exposes `POST /internal/notifications` (new `InternalNotificationController`) delegating to the existing `NotificationService.createNotification(CreateNotificationDTO)`. This is what the user-service `NotificationServiceClient` (built in Phase 1) calls during registration.

7. **In-service notification stays a direct save.** `OrderServiceImpl.createOrder` still persists its `TICKET_BOUGHT` notification via `notificationRepository.save(...)` — `Order` and `Notification` live in the same booking DB, so no Feign needed there.

8. **`NotificationServiceImpl` drops the `User` lookup (§7.1).** `createNotification` no longer loads a `User` (booking has no users); it sets `userId` from the DTO and still links the user's latest `Order` via `orderRepository.findFirstByUserIdOrderByCreatedAtDesc`. The old "User not found" `NotFoundException` path is removed.

9. **`NotificationScheduler` rewrite (§7.3) — zero cross-service calls.** A new `TicketRepository.findBySessionDateAndOrderIsNotNull(LocalDate)` (using the `sessionDate` snapshot column) replaces the catalog `ScreenSession` query; reminder text is built from `ticket.getMovieTitle()`/`ticket.getSessionStartTime()`; the user is `ticket.getOrder().getUserId()`. One notification per (session, user) is preserved.

10. **Phase 3 reuses the existing `cinema` Postgres** (`localhost:5432`) like Phases 1–2; `ddl-auto=update` adds the new snapshot columns. Redis caching is real at runtime, suppressed under the `test` profile (`RedisConfig` is `@Profile("!test")`), and the H2 `application-test.yml` keeps the build self-contained. Per-service DBs/containers are Phase 4.

11. **No `@EnableFeignClients` issue for `common`:** the OpenFeign/Resilience4j starters are declared in booking's own pom (it actively uses Feign), and `spring.cloud.openfeign.circuitbreaker.enabled=true` wires the `@FeignClient(fallback=…)` beans (same pattern as user-service Phase 1).

---

### Task 1: `booking-service` skeleton — POM, app, properties, RedisConfig, SecurityConfig, enums

**Files:**
- Modify: `microservices/pom.xml` (add `<module>booking-service</module>`)
- Create: `microservices/booking-service/pom.xml`
- Create: `microservices/booking-service/src/main/java/com/awbd/cinema/BookingServiceApplication.java`
- Create: `microservices/booking-service/src/main/resources/application.properties`, `application.yml`, `application-test.yml`
- Copy (verbatim): `config/RedisConfig.java`, `enums/OrderStatus.java`, `enums/TicketType.java`
- Create (new): `security/SecurityConfig.java`

- [ ] **Step 1: Add the module to the reactor**

In `microservices/pom.xml`, change:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
    </modules>
```

to add a fourth line:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>catalog-service</module>
        <module>booking-service</module>
    </modules>
```

- [ ] **Step 2: Create `microservices/booking-service/pom.xml`**

Like catalog's pom, but **with** OpenFeign + Resilience4j (booking is a Feign client) and **without** TMDB.

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

    <artifactId>booking-service</artifactId>
    <packaging>jar</packaging>
    <name>booking-service</name>
    <description>Orders, tickets, offers and notifications microservice</description>

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
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
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

- [ ] **Step 3: Create `BookingServiceApplication`**

```java
package com.awbd.cinema;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class BookingServiceApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `application.properties`**

```properties
spring.application.name=booking-service

server.port=8083
server.servlet.context-path=/api/v1

logging.file.name=logs/booking-service.log
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

spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

services.catalog.url=${CATALOG_SERVICE_URL:http://localhost:8082/api/v1}
services.user.url=${USER_SERVICE_URL:http://localhost:8081/api/v1}

spring.cloud.openfeign.circuitbreaker.enabled=true

management.endpoints.web.exposure.include=health,info,metrics,prometheus

logging.level.com.awbd.cinema=DEBUG
```

- [ ] **Step 5: Create `application.yml`** (booking subset of the monolith cache map)

```yaml
app:
  cache:
    default-ttl: 5m
    caches:
      notifications: 30m
      user_notifications: 15m
      offer_lists: 10m
      single_offers: 30m
      ticket_lists: 10m
      single_tickets: 30m
      ticket_infos: 10m
      single_ticket_info: 30m
      single_orders: 1h
      order_lists: 5m
      user_orders: 10m
      user_past_orders: 30m
      user_discount_previews: 2m
```

- [ ] **Step 6: Create `application-test.yml`** (H2 profile; Feign URLs point nowhere — clients are mocked in tests)

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
  cloud:
    openfeign:
      circuitbreaker:
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

services:
  catalog:
    url: http://localhost:8082/api/v1
  user:
    url: http://localhost:8081/api/v1
```

- [ ] **Step 7: Port `RedisConfig` + the 2 booking-only enums verbatim**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/main/java/com/awbd/cinema/config, microservices/booking-service/src/main/java/com/awbd/cinema/enums, microservices/booking-service/src/main/java/com/awbd/cinema/security

Copy-Item cinema/src/main/java/com/awbd/cinema/config/RedisConfig.java microservices/booking-service/src/main/java/com/awbd/cinema/config/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/OrderStatus.java microservices/booking-service/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/TicketType.java microservices/booking-service/src/main/java/com/awbd/cinema/enums/
```

> `RedisConfig` imports `com.awbd.cinema.utils.CacheProperties` (resolves from `common`) and keeps `@Profile("!test")`. `NotificationType` is already in `common` (Phase 1) — do NOT copy it here.

- [ ] **Step 8: Create the validator-only `SecurityConfig`**

Create `microservices/booking-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java` — byte-identical to catalog-service's `SecurityConfig` (no login beans; `/internal/**` permitAll + CSRF-exempt; `anyRequest().authenticated()`):

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

- [ ] **Step 9: Compile checkpoint**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS` (app + RedisConfig + SecurityConfig + enums compile against `common`).

- [ ] **Step 10: Commit**

```bash
git add microservices/pom.xml microservices/booking-service
git commit -m "Scaffold booking-service module: pom, app, properties, RedisConfig, SecurityConfig, enums"
```

---

### Task 2: `booking-service` — entities + repositories (with denormalization)

`TicketInfo`/`Offer`/`PointsSpend` and 5 repositories port verbatim. `Order`/`Notification`/`Ticket` are created with the plain-ID + snapshot changes. `TicketRepository` is authored fresh to add the scheduler's new query.

**Files:**
- Copy (verbatim): `entities/TicketInfo.java`, `entities/Offer.java`, `entities/PointsSpend.java`; `repositories/{OrderRepository,TicketInfoRepository,OfferRepository,PointsSpendRepository,NotificationRepository}.java`
- Create (modified): `entities/Order.java`, `entities/Ticket.java`, `entities/Notification.java`
- Create (port + new method): `repositories/TicketRepository.java`

- [ ] **Step 1: Port the unchanged entities + 5 repositories verbatim**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/main/java/com/awbd/cinema/entities, microservices/booking-service/src/main/java/com/awbd/cinema/repositories

Copy-Item cinema/src/main/java/com/awbd/cinema/entities/TicketInfo.java microservices/booking-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/Offer.java microservices/booking-service/src/main/java/com/awbd/cinema/entities/
Copy-Item cinema/src/main/java/com/awbd/cinema/entities/PointsSpend.java microservices/booking-service/src/main/java/com/awbd/cinema/entities/

Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/OrderRepository.java microservices/booking-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/TicketInfoRepository.java microservices/booking-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/OfferRepository.java microservices/booking-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/PointsSpendRepository.java microservices/booking-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/NotificationRepository.java microservices/booking-service/src/main/java/com/awbd/cinema/repositories/
```

> `OrderRepository.findByUserId…` and `NotificationRepository.findByUserIdOrderByCreatedDateDesc` resolve against the renamed plain `userId` fields (Step 2/4) — no change needed.

- [ ] **Step 2: Create the modified `Order` entity** (`user` → `userId`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/entities/Order.java`:

```java
package com.awbd.cinema.entities;

import com.awbd.cinema.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "payment_at")
    private LocalDateTime paymentAt;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "loyalty_points", nullable = false)
    private Integer loyaltyPoints;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "The user is required.")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "points_spend_id")
    private PointsSpend pointsSpend;

    @OneToMany(mappedBy = "order")
    private List<Ticket> tickets;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;
}
```

- [ ] **Step 3: Create the modified `Ticket` entity** (plain IDs + snapshot fields)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/entities/Ticket.java`:

```java
package com.awbd.cinema.entities;

import com.awbd.cinema.enums.TicketType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "tickets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ticket_seat_room_session",
        columnNames = {"seat_id", "room_id", "session_id"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long id;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TicketType type;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "session_id", nullable = false)
    private Long screenSessionId;

    // Denormalized snapshot of catalog data, fetched once at creation via catalog /internal/ticket-setup
    @Column(name = "seat_row")
    private Integer seatRow;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "seat_zone")
    private String seatZone;

    @Column(name = "extra_fee", precision = 10, scale = 2)
    private BigDecimal extraFee;

    @Column(name = "extra_points")
    private Integer extraPoints;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "session_start_time")
    private LocalTime sessionStartTime;

    @Column(name = "session_points")
    private Integer sessionPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_info_id")
    private TicketInfo ticketInfo;
}
```

- [ ] **Step 4: Create the modified `Notification` entity** (`user` → `userId`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/entities/Notification.java`:

```java
package com.awbd.cinema.entities;

import com.awbd.cinema.enums.NotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @NotNull(message = "Notification type is required.")
    private NotificationType type;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Notification content is required.")
    private String content;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "sent_date")
    private LocalDateTime sentDate;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "The user is required.")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
}
```

> `NotificationType` resolves from `common` (`com.awbd.cinema.enums.NotificationType`).

- [ ] **Step 5: Create `TicketRepository`** (the monolith's methods + the scheduler's new `findBySessionDateAndOrderIsNotNull`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/repositories/TicketRepository.java`:

```java
package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    boolean existsBySeatIdAndRoomIdAndScreenSessionId(Long seatId, Long roomId, Long sessionId);
    Page<Ticket> findByScreenSessionId(Long sessionId, Pageable pageable);
    Page<Ticket> findByRoomId(Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomId(Long sessionId, Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomIdAndIsAvailable(Long sessionId, Long roomId, boolean isAvailable, Pageable pageable);
    List<Ticket> findByScreenSessionIdAndOrderIsNotNull(Long sessionId);
    List<Ticket> findBySessionDateAndOrderIsNotNull(LocalDate sessionDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForBooking(@Param("id") Long id);
}
```

- [ ] **Step 6: Compile checkpoint**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS` (entities + all 6 repositories compile).

- [ ] **Step 7: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/entities microservices/booking-service/src/main/java/com/awbd/cinema/repositories
git commit -m "Add booking-service entities (denormalized Order/Ticket/Notification) and repositories"
```

---

### Task 3: `booking-service` — DTOs

Port the unchanged DTOs; create the three whose `from(...)` reads the renamed fields. `CreateNotificationDTO` is **not** copied — it lives in `common`.

**Files:**
- Copy (verbatim): `OrderDTOs/{CreateOrderDTO,OrderItemDTO,DiscountPreviewDTO}`, `TicketDTOs/{SaveTicketDTO,BookTicketDTO}`, `TicketInfoDTOs/{SaveTicketInfoDTO,TicketInfoDTO}`, `OfferDTOs/{SaveOfferDTO,OfferDTO}`
- Create (modified): `OrderDTOs/OrderDTO.java`, `TicketDTOs/TicketDTO.java`, `NotificationDTOs/NotificationDTO.java`

- [ ] **Step 1: Port the unchanged DTOs verbatim**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OrderDTOs, microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketDTOs, microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketInfoDTOs, microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OfferDTOs, microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/NotificationDTOs

Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/CreateOrderDTO.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/OrderItemDTO.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/DiscountPreviewDTO.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/SaveTicketDTO.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/BookTicketDTO.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/TicketInfoDTOs/*.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketInfoDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/OfferDTOs/*.java microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OfferDTOs/
```

- [ ] **Step 2: Create the modified `OrderDTO`** (`o.getUser().getId()` → `o.getUserId()`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/OrderDTOs/OrderDTO.java`:

```java
package com.awbd.cinema.DTOs.OrderDTOs;

import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDTO(
        Long id,
        LocalDateTime createdAt,
        OrderStatus status,
        LocalDateTime paymentAt,
        LocalDateTime deletedAt,
        BigDecimal price,
        Integer loyaltyPoints,
        Integer pointsUsed,
        BigDecimal discount,
        Long userId,
        List<Long> ticketIds,
        Integer offerPercent,
        String offerMessage
) {
    public static OrderDTO from(Order o) {
        List<Long> ticketIds = o.getTickets() == null ? List.of() :
                o.getTickets().stream().map(t -> t.getId()).toList();
        Integer pointsUsed = o.getPointsSpend() != null ? o.getPointsSpend().getPointsUsed() : null;
        BigDecimal discount = o.getPointsSpend() != null ? o.getPointsSpend().getDiscount() : null;
        Integer offerPercent = o.getOffer() != null ? o.getOffer().getPercent() : null;
        String offerMessage = o.getOffer() != null
                ? "A " + o.getOffer().getPercent() + "% discount was applied for placing your order on " + o.getOffer().getDay() + "!"
                : null;
        return new OrderDTO(
                o.getId(),
                o.getCreatedAt(),
                o.getStatus(),
                o.getPaymentAt(),
                o.getDeletedAt(),
                o.getPrice(),
                o.getLoyaltyPoints(),
                pointsUsed,
                discount,
                o.getUserId(),
                ticketIds,
                offerPercent,
                offerMessage
        );
    }
}
```

- [ ] **Step 3: Create the modified `TicketDTO`** (`t.getSeat().getId()` etc. → `t.getSeatId()` etc.)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/TicketDTOs/TicketDTO.java`:

```java
package com.awbd.cinema.DTOs.TicketDTOs;

import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.TicketType;

import java.math.BigDecimal;

public record TicketDTO(
        Long id,
        boolean isAvailable,
        Long seatId,
        Long roomId,
        Long screenSessionId,
        TicketType type,
        BigDecimal price
) {
    public static TicketDTO from(Ticket t) {
        BigDecimal price = t.getTicketInfo() != null ? t.getTicketInfo().getPrice() : null;
        return new TicketDTO(
                t.getId(),
                t.isAvailable(),
                t.getSeatId(),
                t.getRoomId(),
                t.getScreenSessionId(),
                t.getType(),
                price
        );
    }
}
```

- [ ] **Step 4: Create the modified `NotificationDTO`** (`n.getUser().getId()` → `n.getUserId()`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/DTOs/NotificationDTOs/NotificationDTO.java`:

```java
package com.awbd.cinema.DTOs.NotificationDTOs;

import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        NotificationType type,
        String content,
        LocalDateTime createdDate,
        LocalDateTime sentDate,
        Long userId,
        OrderDTO order
) {
    public static NotificationDTO from(Notification n) {
        OrderDTO orderDTO = n.getOrder() != null ? OrderDTO.from(n.getOrder()) : null;
        return new NotificationDTO(
                n.getId(),
                n.getType(),
                n.getContent(),
                n.getCreatedDate(),
                n.getSentDate(),
                n.getUserId(),
                orderDTO
        );
    }
}
```

- [ ] **Step 5: Compile checkpoint**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/DTOs
git commit -m "Add booking-service DTOs (OrderDTO/TicketDTO/NotificationDTO read denormalized fields)"
```

---

### Task 4: `booking-service` — Feign clients (catalog + user) with fallbacks

Two `@FeignClient`s with `@Component` fallbacks. They use the shared DTOs from `common` (`TicketSetupDTO`, `LoyaltyPointsDTO`, `AdjustLoyaltyPointsDTO`).

**Files:**
- Create: `clients/CatalogServiceClient.java`, `clients/CatalogServiceClientFallback.java`, `clients/UserServiceClient.java`, `clients/UserServiceClientFallback.java`

- [ ] **Step 1: Create `CatalogServiceClient`** (integration ① — ticket setup)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClient.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "catalog-service",
        url = "${services.catalog.url}",
        fallback = CatalogServiceClientFallback.class
)
public interface CatalogServiceClient {

    @GetMapping("/internal/ticket-setup")
    TicketSetupDTO getTicketSetup(
            @RequestParam Long seatId,
            @RequestParam Long roomId,
            @RequestParam Long sessionId);
}
```

> `TicketSetupDTO` lives in `common` at package `com.awbd.cinema.DTOs.TicketDTOs` (added Phase 1/2).

- [ ] **Step 2: Create `CatalogServiceClientFallback`** (ticket creation is admin-only — fail clearly)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClientFallback.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CatalogServiceClientFallback implements CatalogServiceClient {

    @Override
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}).", seatId, roomId, sessionId);
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }
}
```

- [ ] **Step 3: Create `UserServiceClient`** (integration ② — loyalty points)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClient.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "user-service",
        url = "${services.user.url}",
        fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    @GetMapping("/internal/users/{id}/loyalty-points")
    LoyaltyPointsDTO getLoyaltyPoints(@PathVariable Long id);

    @PatchMapping("/internal/users/{id}/loyalty-points")
    LoyaltyPointsDTO updateLoyaltyPoints(@PathVariable Long id, @RequestBody AdjustLoyaltyPointsDTO dto);
}
```

> `LoyaltyPointsDTO`/`AdjustLoyaltyPointsDTO` live in `common` at `com.awbd.cinema.DTOs.UserDTOs` (added Phase 1).

- [ ] **Step 4: Create `UserServiceClientFallback`** (loyalty unavailable → no discount / no award, never fail the order)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClientFallback.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
        log.warn("user-service unavailable; treating loyalty points as 0 for user {}.", id);
        return new LoyaltyPointsDTO(id, 0);
    }

    @Override
    public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
        log.warn("user-service unavailable; skipping loyalty-points update for user {}.", id);
        return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
    }
}
```

- [ ] **Step 5: Compile checkpoint**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/clients
git commit -m "Add booking-service Feign clients for catalog (ticket-setup) and user (loyalty) with fallbacks"
```

---

### Task 5: `booking-service` — services

Port `TicketInfoService`/`OfferService` (+impls) and the 3 unchanged interfaces verbatim. Author the three Feign-rewritten impls: `TicketServiceImpl` (catalog), `OrderServiceImpl` (user loyalty + snapshot reads), `NotificationServiceImpl` (drop User load).

**Files:**
- Copy (verbatim): `services/TicketInfoService/{TicketInfoService,TicketInfoServiceImpl}.java`, `services/OfferService/{OfferService,OfferServiceImpl}.java`, `services/OrderService/OrderService.java`, `services/TicketService/TicketService.java`, `services/NotificationService/NotificationService.java`
- Create (modified): `services/TicketService/TicketServiceImpl.java`, `services/OrderService/OrderServiceImpl.java`, `services/NotificationService/NotificationServiceImpl.java`

- [ ] **Step 1: Port the unchanged services + 3 interfaces verbatim**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketInfoService, microservices/booking-service/src/main/java/com/awbd/cinema/services/OfferService, microservices/booking-service/src/main/java/com/awbd/cinema/services/OrderService, microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketService, microservices/booking-service/src/main/java/com/awbd/cinema/services/NotificationService

Copy-Item cinema/src/main/java/com/awbd/cinema/services/TicketInfoService/*.java microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketInfoService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/OfferService/*.java microservices/booking-service/src/main/java/com/awbd/cinema/services/OfferService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/OrderService/OrderService.java microservices/booking-service/src/main/java/com/awbd/cinema/services/OrderService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/TicketService/TicketService.java microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketService/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/NotificationService/NotificationService.java microservices/booking-service/src/main/java/com/awbd/cinema/services/NotificationService/
```

- [ ] **Step 2: Create the modified `TicketServiceImpl`** (catalog Feign for ticket setup)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketService/TicketServiceImpl.java`:

```java
package com.awbd.cinema.services.TicketService;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.clients.CatalogServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketInfoRepository ticketInfoRepository;
    private final CatalogServiceClient catalogServiceClient;

    @Override
    @Transactional
    @CacheEvict(value = "ticket_lists", allEntries = true)
    public TicketDTO createTicket(SaveTicketDTO dto) {
        if (ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(dto.seatId(), dto.roomId(), dto.screenSessionId())) {
            throw new AlreadyExistsException("A ticket for this seat, room and session already exists.");
        }

        // Validate (seat-in-room, session-in-room) and fetch the pricing/display snapshot from catalog-service.
        TicketSetupDTO setup = catalogServiceClient.getTicketSetup(dto.seatId(), dto.roomId(), dto.screenSessionId());

        Ticket ticket = Ticket.builder()
                .isAvailable(true)
                .seatId(dto.seatId())
                .roomId(dto.roomId())
                .screenSessionId(dto.screenSessionId())
                .seatRow(setup.seatRow())
                .seatNumber(setup.seatNumber())
                .seatZone(setup.seatZone())
                .extraFee(setup.extraFee())
                .extraPoints(setup.extraPoints())
                .movieTitle(setup.movieTitle())
                .sessionDate(setup.sessionDate())
                .sessionStartTime(setup.sessionStartTime())
                .sessionPoints(setup.sessionPoints())
                .build();

        return TicketDTO.from(ticketRepository.save(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ticket_lists")
    public RestPage<TicketDTO> getTickets(Long sessionId, Long roomId, Boolean isAvailable, Pageable pageable) {
        if (sessionId != null && roomId != null && isAvailable != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(sessionId, roomId, isAvailable, pageable)
                    .map(TicketDTO::from));
        }
        if (sessionId != null && roomId != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionIdAndRoomId(sessionId, roomId, pageable).map(TicketDTO::from));
        }
        if (sessionId != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionId(sessionId, pageable).map(TicketDTO::from));
        }
        if (roomId != null) {
            return new RestPage<>(ticketRepository.findByRoomId(roomId, pageable).map(TicketDTO::from));
        }
        return new RestPage<>(ticketRepository.findAll(pageable).map(TicketDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_ticket", key = "#id")
    public TicketDTO getTicket(Long id) {
        return ticketRepository.findById(id)
                .map(TicketDTO::from)
                .orElseThrow(() -> new NotFoundException("Ticket not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_ticket", key = "#id"),
            @CacheEvict(value = "ticket_lists", allEntries = true)
    })
    public TicketDTO bookTicket(Long id, BookTicketDTO dto) {
        Ticket ticket = ticketRepository.findByIdForBooking(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found."));

        if (!ticket.isAvailable()) {
            throw new BadRequestException("Ticket is already booked.");
        }

        TicketInfo info = ticketInfoRepository.findByType(dto.type())
                .orElseThrow(() -> new NotFoundException(
                        "No price configured for type '" + dto.type() + "'."));

        ticket.setType(dto.type());
        ticket.setTicketInfo(info);
        ticket.setAvailable(false);
        return TicketDTO.from(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_ticket", key = "#id"),
            @CacheEvict(value = "ticket_lists", allEntries = true)
    })
    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new NotFoundException("Ticket not found.");
        }
        ticketRepository.deleteById(id);
    }
}
```

- [ ] **Step 3: Create the modified `OrderServiceImpl`** (user loyalty Feign + snapshot reads; `userId` instead of `User`)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/services/OrderService/OrderServiceImpl.java`:

```java
package com.awbd.cinema.services.OrderService;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceClient;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketInfoRepository ticketInfoRepository;
    private final UserServiceClient userServiceClient;
    private final OfferRepository offerRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "ticket_lists", allEntries = true),
            @CacheEvict(value = "single_ticket", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#userId"),
            @CacheEvict(value = "user_notifications", key = "#userId")
    })
    public OrderDTO createOrder(CreateOrderDTO dto, Long userId) {
        List<Ticket> tickets = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalPoints = 0;

        for (OrderItemDTO item : dto.items()) {
            Ticket ticket = ticketRepository.findById(item.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket " + item.ticketId() + " not found."));

            if (!ticket.isAvailable()) {
                log.warn("User {} attempted to order ticket {} which is no longer available.", userId, item.ticketId());
                throw new BadRequestException("Ticket " + item.ticketId() + " is no longer available.");
            }

            TicketInfo info = ticketInfoRepository.findByType(item.type())
                    .orElseThrow(() -> new NotFoundException(
                            "No price configured for type '" + item.type() + "'."));

            BigDecimal ticketPrice = info.getPrice().add(ticket.getExtraFee());
            int ticketPoints = ticket.getExtraPoints() + ticket.getSessionPoints();

            ticket.setType(item.type());
            ticket.setTicketInfo(info);
            ticket.setAvailable(false);
            totalPrice = totalPrice.add(ticketPrice);
            totalPoints += ticketPoints;
            tickets.add(ticket);
        }

        PointsSpend pointsSpend = null;
        if (dto.useDiscount()) {
            int currentLoyalty = userServiceClient.getLoyaltyPoints(userId).loyaltyPoints();
            if (currentLoyalty > 0) {
                int pointsToSpend = currentLoyalty;
                BigDecimal discount = PointsSpend.calculateDiscount(pointsToSpend);
                pointsSpend = PointsSpend.builder()
                        .pointsUsed(pointsToSpend)
                        .discount(discount)
                        .build();
                totalPrice = totalPrice.subtract(discount).max(BigDecimal.ZERO);
                userServiceClient.updateLoyaltyPoints(userId, new AdjustLoyaltyPointsDTO(0));
            }
        }

        Offer offer = offerRepository.findByDay(LocalDateTime.now().getDayOfWeek()).orElse(null);
        if (offer != null) {
            BigDecimal offerDiscount = totalPrice
                    .multiply(BigDecimal.valueOf(offer.getPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalPrice = totalPrice.subtract(offerDiscount).max(BigDecimal.ZERO);
        }

        Order order = Order.builder()
                .createdAt(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .price(totalPrice)
                .loyaltyPoints(totalPoints)
                .pointsSpend(pointsSpend)
                .offer(offer)
                .userId(userId)
                .build();

        Order savedOrder = orderRepository.save(order);

        for (Ticket ticket : tickets) {
            ticket.setOrder(savedOrder);
            ticketRepository.save(ticket);
        }

        savedOrder.setTickets(tickets);

        String ticketDetails = tickets.stream()
                .map(t -> "\"" + t.getMovieTitle() + "\""
                        + " on " + t.getSessionDate()
                        + " at " + t.getSessionStartTime()
                        + ", Row " + t.getSeatRow()
                        + " Seat " + t.getSeatNumber()
                        + " (" + t.getSeatZone() + ")"
                        + " [" + t.getType() + "]")
                .collect(Collectors.joining("\n- ", "- ", ""));

        Notification ticketBoughtNotification = Notification.builder()
                .type(NotificationType.TICKET_BOUGHT)
                .content("Your order has been confirmed! You purchased " + tickets.size()
                        + " ticket(s):\n" + ticketDetails)
                .createdDate(LocalDateTime.now())
                .sentDate(LocalDateTime.now())
                .userId(userId)
                .order(savedOrder)
                .build();
        notificationRepository.save(ticketBoughtNotification);

        return OrderDTO.from(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "order_lists")
    public RestPage<OrderDTO> getOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            return new RestPage<>(orderRepository.findByStatus(orderStatus, pageable).map(OrderDTO::from));
        }
        return new RestPage<>(orderRepository.findAll(pageable).map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_orders")
    public RestPage<OrderDTO> getMyOrders(Long userId, Pageable pageable) {
        return new RestPage<>(orderRepository.findByUserId(userId, pageable).map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_discount_previews", key = "#userId")
    public DiscountPreviewDTO getDiscountPreview(Long userId) {
        LoyaltyPointsDTO loyalty = userServiceClient.getLoyaltyPoints(userId);
        int points = loyalty.loyaltyPoints();
        BigDecimal discount = PointsSpend.calculateDiscount(points);
        return new DiscountPreviewDTO(points, discount);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_past_orders")
    public RestPage<OrderDTO> getMyPastOrders(Long userId, Pageable pageable) {
        return new RestPage<>(orderRepository.findByUserIdAndStatusIn(
                userId, List.of(OrderStatus.PAID, OrderStatus.CANCELLED), pageable)
                .map(OrderDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_orders", key = "#id")
    public OrderDTO getOrder(Long id) {
        return orderRepository.findById(id)
                .filter(o -> o.getDeletedAt() == null)
                .map(OrderDTO::from)
                .orElseThrow(() -> new NotFoundException("Order not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true),
            @CacheEvict(value = "user_discount_previews", key = "#result.userId()")
    })
    public OrderDTO payOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING orders can be paid.");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaymentAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        LoyaltyPointsDTO loyalty = userServiceClient.getLoyaltyPoints(saved.getUserId());
        userServiceClient.updateLoyaltyPoints(saved.getUserId(),
                new AdjustLoyaltyPointsDTO(loyalty.loyaltyPoints() + saved.getLoyaltyPoints()));

        return OrderDTO.from(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true),
            @CacheEvict(value = "ticket_lists", allEntries = true),
            @CacheEvict(value = "single_ticket", allEntries = true)
    })
    public OrderDTO cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled.");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BadRequestException("Paid orders cannot be cancelled.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getTickets() != null) {
            for (Ticket ticket : order.getTickets()) {
                ticket.setAvailable(true);
                ticket.setType(null);
                ticket.setOrder(null);
                ticketRepository.save(ticket);
            }
        }
        return OrderDTO.from(orderRepository.save(order));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_orders", key = "#id"),
            @CacheEvict(value = "order_lists", allEntries = true),
            @CacheEvict(value = "user_orders", allEntries = true),
            @CacheEvict(value = "user_past_orders", allEntries = true)
    })
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
```

- [ ] **Step 4: Create the modified `NotificationServiceImpl`** (no User load; `userId` from DTO)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/services/NotificationService/NotificationServiceImpl.java`:

```java
package com.awbd.cinema.services.NotificationService;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    @CacheEvict(value = "user_notifications", key = "#dto.userId()")
    public NotificationDTO createNotification(CreateNotificationDTO dto) {
        Order lastOrder = orderRepository.findFirstByUserIdOrderByCreatedAtDesc(dto.userId()).orElse(null);

        Notification notification = Notification.builder()
                .type(dto.type())
                .content(dto.content())
                .createdDate(LocalDateTime.now())
                .userId(dto.userId())
                .order(lastOrder)
                .build();

        return NotificationDTO.from(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_notifications")
    public RestPage<NotificationDTO> getMyNotifications(Long userId, Pageable pageable) {
        return new RestPage<>(notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable)
                .map(NotificationDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "notification", key = "#id")
    public NotificationDTO getNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found with id: " + id));
        return NotificationDTO.from(notification);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "notification", key = "#id"),
            @CacheEvict(value = "user_notifications", key = "#result.userId()")
    })
    public NotificationDTO markAsSent(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found with id: " + id));
        notification.setSentDate(LocalDateTime.now());
        return NotificationDTO.from(notificationRepository.save(notification));
    }
}
```

- [ ] **Step 5: Compile checkpoint**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS` (all services compile; controllers come next).

- [ ] **Step 6: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/services
git commit -m "Add booking-service services (Feign-based Ticket/Order, User-free Notification)"
```

---

### Task 6: `booking-service` — controllers, internal notification endpoint, rewritten scheduler

Port the 5 controllers verbatim, add the `/internal/notifications` endpoint (integration ③ server side), and rewrite the scheduler to be fully booking-local. After this, the whole main source compiles.

**Files:**
- Copy (verbatim): `controllers/{OrderController,TicketController,TicketInfoController,OfferController,NotificationController}.java`
- Create: `controllers/InternalNotificationController.java`
- Create (rewritten): `schedulers/NotificationScheduler.java`

- [ ] **Step 1: Port the 5 controllers verbatim**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/main/java/com/awbd/cinema/controllers, microservices/booking-service/src/main/java/com/awbd/cinema/schedulers

Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/OrderController.java microservices/booking-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/TicketController.java microservices/booking-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/TicketInfoController.java microservices/booking-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/OfferController.java microservices/booking-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/NotificationController.java microservices/booking-service/src/main/java/com/awbd/cinema/controllers/
```

> These controllers use `@AuthenticationPrincipal CustomUserDetails` (from `common`), the booking DTOs, and `RestPage` — all resolvable. `OrderController` reads `userDetails.getId()` for the current user's id, which is exactly the `Long userId` the services now expect.

- [ ] **Step 2: Create `InternalNotificationController`** (integration ③ server side)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/controllers/InternalNotificationController.java`:

```java
package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.services.NotificationService.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping("/notifications")
    public ResponseEntity<NotificationDTO> createNotification(@Valid @RequestBody CreateNotificationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(dto));
    }
}
```

> `CreateNotificationDTO` resolves from `common`. This is the endpoint the user-service `NotificationServiceClient` (Phase 1) calls during registration; its Feign method has a `void` return, so it ignores the response body.

- [ ] **Step 3: Create the rewritten `NotificationScheduler`** (booking-local, snapshot-based — §7.3)

Create `microservices/booking-service/src/main/java/com/awbd/cinema/schedulers/NotificationScheduler.java`:

```java
package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendMovieReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Ticket> bookedTickets = ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow);

        // one notification per user per session, using the denormalized snapshot fields on the ticket
        Map<String, Ticket> oneTicketPerSessionUser = new LinkedHashMap<>();
        for (Ticket ticket : bookedTickets) {
            Long userId = ticket.getOrder().getUserId();
            String key = ticket.getScreenSessionId() + ":" + userId;
            oneTicketPerSessionUser.putIfAbsent(key, ticket);
        }

        for (Ticket ticket : oneTicketPerSessionUser.values()) {
            Order order = ticket.getOrder();
            String content = "Reminder: you have a ticket for \"" + ticket.getMovieTitle()
                    + "\" tomorrow at " + ticket.getSessionStartTime() + ". Enjoy the show!";

            Notification notification = Notification.builder()
                    .type(NotificationType.MOVIE_REMINDER)
                    .content(content)
                    .createdDate(LocalDateTime.now())
                    .sentDate(LocalDateTime.now())
                    .userId(order.getUserId())
                    .order(order)
                    .build();

            notificationRepository.save(notification);
        }
    }
}
```

- [ ] **Step 4: Compile the full main source**

From `microservices/` run: `mvn -pl booking-service -am compile`
Expected: `BUILD SUCCESS` — the entire booking-service main tree compiles.

- [ ] **Step 5: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/controllers microservices/booking-service/src/main/java/com/awbd/cinema/schedulers
git commit -m "Add booking-service controllers, internal notifications endpoint, and rewritten scheduler"
```

---

### Task 7: `booking-service` — service-layer unit tests

Port the two unchanged service tests; rewrite the four whose subjects changed (Feign + denormalization + `userId`).

**Files:**
- Copy (verbatim): `services/TicketInfoServiceTest.java`, `services/OfferServiceTest.java`
- Create (rewritten): `services/TicketServiceTest.java`, `services/OrderServiceTest.java`, `services/NotificationServiceTest.java`, `schedulers/NotificationSchedulerTest.java`

- [ ] **Step 1: Port the two unchanged service tests**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/test/java/com/awbd/cinema/services, microservices/booking-service/src/test/java/com/awbd/cinema/schedulers

Copy-Item cinema/src/test/java/com/awbd/cinema/services/TicketInfoServiceTest.java microservices/booking-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/OfferServiceTest.java microservices/booking-service/src/test/java/com/awbd/cinema/services/
```

- [ ] **Step 2: Write the rewritten `TicketServiceTest`** (mocks `CatalogServiceClient` instead of catalog repos)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/services/TicketServiceTest.java`:

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.clients.CatalogServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.services.TicketService.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketInfoRepository ticketInfoRepository;
    @Mock private CatalogServiceClient catalogServiceClient;

    @InjectMocks private TicketServiceImpl ticketService;

    private Ticket sampleTicket;
    private TicketInfo sampleTicketInfo;
    private TicketSetupDTO sampleSetup;

    @BeforeEach
    void setUp() {
        sampleTicketInfo = TicketInfo.builder()
                .id(4L).type(TicketType.ADULT).price(BigDecimal.valueOf(12.50)).build();

        sampleTicket = Ticket.builder()
                .id(100L).isAvailable(true)
                .seatId(1L).roomId(2L).screenSessionId(3L)
                .build();

        sampleSetup = new TicketSetupDTO(5, 10, "A", BigDecimal.ZERO, 0,
                "Inception", LocalDate.of(2026, 7, 1), LocalTime.of(19, 30), 0);
    }

    @Nested
    @DisplayName("Create Ticket Tests")
    class CreateTicketTests {

        private SaveTicketDTO saveDto;

        @BeforeEach
        void setup() {
            saveDto = new SaveTicketDTO(1L, 2L, 3L);
        }

        @Test
        @DisplayName("Should create a ticket from the catalog snapshot")
        void createTicket_Success() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(false);
            when(catalogServiceClient.getTicketSetup(1L, 2L, 3L)).thenReturn(sampleSetup);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(sampleTicket);

            TicketDTO result = ticketService.createTicket(saveDto);

            assertNotNull(result);
            assertEquals(100L, result.id());
            assertTrue(result.isAvailable());
            verify(catalogServiceClient).getTicketSetup(1L, 2L, 3L);
            verify(ticketRepository, times(1)).save(any(Ticket.class));
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException and skip the catalog call when ticket exists")
        void createTicket_AlreadyExists() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(true);

            assertThrows(AlreadyExistsException.class, () -> ticketService.createTicket(saveDto));
            verify(catalogServiceClient, never()).getTicketSetup(any(), any(), any());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should propagate a clear error when catalog-service is unavailable")
        void createTicket_CatalogUnavailable() {
            when(ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(1L, 2L, 3L)).thenReturn(false);
            when(catalogServiceClient.getTicketSetup(1L, 2L, 3L))
                    .thenThrow(new BadRequestException("Catalog service is currently unavailable. Please try again later."));

            assertThrows(BadRequestException.class, () -> ticketService.createTicket(saveDto));
            verify(ticketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Tickets (Filtered/Paged) Tests")
    class GetTicketsTests {

        private final Pageable pageable = PageRequest.of(0, 10);
        private Page<Ticket> ticketPage;

        @BeforeEach
        void setup() {
            ticketPage = new PageImpl<>(Collections.singletonList(sampleTicket));
        }

        @Test
        void getTickets_AllFilters() {
            when(ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable)).thenReturn(ticketPage);
            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, true, pageable);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(ticketRepository).findByScreenSessionIdAndRoomIdAndIsAvailable(3L, 2L, true, pageable);
        }

        @Test
        void getTickets_SessionAndRoom() {
            when(ticketRepository.findByScreenSessionIdAndRoomId(3L, 2L, pageable)).thenReturn(ticketPage);
            Page<TicketDTO> result = ticketService.getTickets(3L, 2L, null, pageable);
            assertNotNull(result);
            verify(ticketRepository).findByScreenSessionIdAndRoomId(3L, 2L, pageable);
        }

        @Test
        void getTickets_NoFilters() {
            when(ticketRepository.findAll(pageable)).thenReturn(ticketPage);
            Page<TicketDTO> result = ticketService.getTickets(null, null, null, pageable);
            assertNotNull(result);
            verify(ticketRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("Get Single Ticket Tests")
    class GetTicketTests {
        @Test
        void getTicket_Success() {
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(sampleTicket));
            TicketDTO result = ticketService.getTicket(100L);
            assertNotNull(result);
            assertEquals(100L, result.id());
        }

        @Test
        void getTicket_NotFound() {
            when(ticketRepository.findById(100L)).thenReturn(Optional.empty());
            assertThrows(NotFoundException.class, () -> ticketService.getTicket(100L));
        }
    }

    @Nested
    @DisplayName("Book Ticket Tests")
    class BookTicketTests {
        private BookTicketDTO bookDto;

        @BeforeEach
        void setup() {
            bookDto = new BookTicketDTO(TicketType.ADULT);
        }

        @Test
        void bookTicket_Success() {
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(sampleTicketInfo));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketDTO result = ticketService.bookTicket(100L, bookDto);

            assertNotNull(result);
            assertFalse(result.isAvailable());
            assertEquals(TicketType.ADULT, result.type());
            assertEquals(BigDecimal.valueOf(12.50), result.price());
            verify(ticketRepository).save(sampleTicket);
        }

        @Test
        void bookTicket_AlreadyBooked() {
            sampleTicket.setAvailable(false);
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            assertThrows(BadRequestException.class, () -> ticketService.bookTicket(100L, bookDto));
            verify(ticketRepository, never()).save(any());
        }

        @Test
        void bookTicket_TicketInfoNotFound() {
            when(ticketRepository.findByIdForBooking(100L)).thenReturn(Optional.of(sampleTicket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.empty());
            assertThrows(NotFoundException.class, () -> ticketService.bookTicket(100L, bookDto));
        }
    }

    @Nested
    @DisplayName("Delete Ticket Tests")
    class DeleteTicketTests {
        @Test
        void deleteTicket_Success() {
            when(ticketRepository.existsById(100L)).thenReturn(true);
            doNothing().when(ticketRepository).deleteById(100L);
            assertDoesNotThrow(() -> ticketService.deleteTicket(100L));
            verify(ticketRepository, times(1)).deleteById(100L);
        }

        @Test
        void deleteTicket_NotFound() {
            when(ticketRepository.existsById(100L)).thenReturn(false);
            assertThrows(NotFoundException.class, () -> ticketService.deleteTicket(100L));
            verify(ticketRepository, never()).deleteById(any());
        }
    }
}
```

- [ ] **Step 3: Write the rewritten `OrderServiceTest`** (mocks `UserServiceClient`; tickets carry snapshot fields; `userId` not `User`)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/services/OrderServiceTest.java`:

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.DiscountPreviewDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceClient;
import com.awbd.cinema.entities.*;
import com.awbd.cinema.enums.OrderStatus;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.*;
import com.awbd.cinema.services.OrderService.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketInfoRepository ticketInfoRepository;
    @Mock private UserServiceClient userServiceClient;
    @Mock private OfferRepository offerRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private OrderServiceImpl orderService;

    private static final Long USER_ID = 1L;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }

    private Ticket availableTicket(Long id) {
        return Ticket.builder()
                .id(id).isAvailable(true)
                .seatId(10L).roomId(20L).screenSessionId(30L)
                .seatRow(1).seatNumber(1).seatZone("A")
                .extraFee(BigDecimal.ZERO).extraPoints(0).sessionPoints(0)
                .movieTitle("Test Movie").sessionDate(LocalDate.now()).sessionStartTime(LocalTime.NOON)
                .build();
    }

    @Nested
    @DisplayName("createOrder Tests")
    class CreateOrderTests {

        @Test
        void createOrder_TicketNotFound_ThrowsNotFoundException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(99L, TicketType.ADULT)), false);
            when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(dto, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Ticket 99 not found.");
        }

        @Test
        void createOrder_TicketNotAvailable_ThrowsBadRequestException() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            Ticket unavailable = Ticket.builder().id(1L).isAvailable(false).build();
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(unavailable));

            assertThatThrownBy(() -> orderService.createOrder(dto, USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Ticket 1 is no longer available.");
        }

        @Test
        @DisplayName("Should create an order without discount/offer, save the confirmation notification, and never call user-service")
        void createOrder_Success_NoDiscountNoOffer() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), false);
            Ticket ticket = availableTicket(1L);
            TicketInfo ticketInfo = TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(50.00)).build();
            Order savedOrder = Order.builder().id(10L).status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(50.00)).userId(USER_ID).tickets(List.of(ticket)).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(ticketInfo));
            when(offerRepository.findByDay(any(DayOfWeek.class))).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderDTO result = orderService.createOrder(dto, USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.price()).isEqualByComparingTo("50.00");
            verify(notificationRepository, times(1)).save(any(Notification.class));
            verify(userServiceClient, never()).getLoyaltyPoints(any());
        }

        @Test
        @DisplayName("Should apply loyalty discount and reset points via user-service when requested")
        void createOrder_Success_WithLoyaltyDiscount() {
            CreateOrderDTO dto = new CreateOrderDTO(List.of(new OrderItemDTO(1L, TicketType.ADULT)), true);
            Ticket ticket = availableTicket(1L);
            TicketInfo ticketInfo = TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(50.00)).build();
            Order savedOrder = Order.builder().id(10L).status(OrderStatus.PENDING)
                    .price(BigDecimal.valueOf(40.00)).userId(USER_ID).build();

            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketInfoRepository.findByType(TicketType.ADULT)).thenReturn(Optional.of(ticketInfo));
            when(offerRepository.findByDay(any(DayOfWeek.class))).thenReturn(Optional.empty());
            when(userServiceClient.getLoyaltyPoints(USER_ID)).thenReturn(new LoyaltyPointsDTO(USER_ID, 100));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            orderService.createOrder(dto, USER_ID);

            verify(userServiceClient).updateLoyaltyPoints(eq(USER_ID), argThat(d -> d.loyaltyPoints() == 0));
        }
    }

    @Nested
    @DisplayName("getOrders and Query Tests")
    class GetOrdersTests {

        @Test
        void getOrders_BlankStatus_ReturnsAllOrders() {
            Order order = Order.builder().id(1L).userId(USER_ID).price(BigDecimal.TEN).build();
            when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderDTO> result = orderService.getOrders("", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findAll(pageable);
        }

        @Test
        void getOrders_WithValidStatus_ReturnsFilteredOrders() {
            Order order = Order.builder().id(1L).status(OrderStatus.PAID).userId(USER_ID).price(BigDecimal.TEN).build();
            when(orderRepository.findByStatus(OrderStatus.PAID, pageable)).thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderDTO> result = orderService.getOrders("paid", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository, times(1)).findByStatus(OrderStatus.PAID, pageable);
        }

        @Test
        void getDiscountPreview_ValidUser_ReturnsCorrectPreview() {
            when(userServiceClient.getLoyaltyPoints(USER_ID)).thenReturn(new LoyaltyPointsDTO(USER_ID, 50));

            DiscountPreviewDTO preview = orderService.getDiscountPreview(USER_ID);

            assertThat(preview.loyaltyPoints()).isEqualTo(50);
            assertThat(preview.potentialDiscount()).isEqualByComparingTo("5.00");
        }
    }

    @Nested
    @DisplayName("Order State Action Tests")
    class OrderStateActionTests {

        @Test
        void payOrder_PendingOrder_CreditsLoyaltyViaUserService() {
            Order pendingOrder = Order.builder().id(5L).status(OrderStatus.PENDING)
                    .loyaltyPoints(15).price(BigDecimal.valueOf(100.00)).userId(USER_ID).build();

            when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userServiceClient.getLoyaltyPoints(USER_ID)).thenReturn(new LoyaltyPointsDTO(USER_ID, 10));

            OrderDTO result = orderService.payOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.PAID);
            verify(userServiceClient).updateLoyaltyPoints(eq(USER_ID), argThat(d -> d.loyaltyPoints() == 25));
        }

        @Test
        void payOrder_NotPending_ThrowsBadRequestException() {
            Order paidOrder = Order.builder().id(5L).status(OrderStatus.PAID).build();
            when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> orderService.payOrder(5L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only PENDING orders can be paid.");
        }

        @Test
        void cancelOrder_PendingOrder_Success() {
            Ticket securedTicket = Ticket.builder().id(100L).isAvailable(false).type(TicketType.ADULT).build();
            List<Ticket> mutableTickets = new ArrayList<>(List.of(securedTicket));
            Order pendingOrder = Order.builder().id(5L).status(OrderStatus.PENDING)
                    .tickets(mutableTickets).userId(USER_ID).price(BigDecimal.TEN).build();
            securedTicket.setOrder(pendingOrder);

            when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDTO result = orderService.cancelOrder(5L);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(securedTicket.isAvailable()).isTrue();
            assertThat(securedTicket.getType()).isNull();
            assertThat(securedTicket.getOrder()).isNull();
            verify(ticketRepository, times(1)).save(securedTicket);
        }

        @Test
        void deleteOrder_ValidOrder_SetsDeletedAt() {
            Order order = Order.builder().id(5L).userId(USER_ID).build();
            when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

            orderService.deleteOrder(5L);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }
    }
}
```

- [ ] **Step 4: Write the rewritten `NotificationServiceTest`** (no `UserRepository`; `userId` from DTO)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/services/NotificationServiceTest.java`:

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.OrderRepository;
import com.awbd.cinema.services.NotificationService.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("Tests for createNotification")
    class CreateNotificationTests {

        @Test
        void createNotification_WithExistingOrder_ReturnsNotificationDTO() {
            Long userId = 1L;
            Long orderId = 100L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.TICKET_BOUGHT, "Your ticket is confirmed!", userId);

            Order lastOrder = Order.builder().id(orderId).price(BigDecimal.TEN).userId(userId).build();

            Notification savedNotification = Notification.builder()
                    .id(50L).type(dto.type()).content(dto.content())
                    .createdDate(LocalDateTime.now()).userId(userId).order(lastOrder).build();

            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(lastOrder));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            NotificationDTO result = notificationService.createNotification(dto);

            assertNotNull(result);
            assertEquals(50L, result.id());
            assertEquals(userId, result.userId());
            assertNotNull(result.order());
            assertEquals(orderId, result.order().id());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification captured = captor.getValue();
            assertEquals(dto.type(), captured.getType());
            assertEquals(dto.content(), captured.getContent());
            assertEquals(userId, captured.getUserId());
            assertEquals(lastOrder, captured.getOrder());
            assertNotNull(captured.getCreatedDate());
        }

        @Test
        void createNotification_WithNoOrders_ReturnsNotificationDTO_WithNullOrder() {
            Long userId = 1L;
            CreateNotificationDTO dto = new CreateNotificationDTO(NotificationType.MOVIE_REMINDER, "Don't miss your show!", userId);

            Notification savedNotification = Notification.builder()
                    .id(51L).type(dto.type()).content(dto.content())
                    .createdDate(LocalDateTime.now()).userId(userId).order(null).build();

            when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            NotificationDTO result = notificationService.createNotification(dto);

            assertNotNull(result);
            assertEquals(51L, result.id());
            assertNull(result.order());

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertNull(captor.getValue().getOrder());
            assertEquals(userId, captor.getValue().getUserId());
        }
    }

    @Nested
    @DisplayName("Tests for getMyNotifications")
    class GetMyNotificationsTests {
        @Test
        void getMyNotifications_ReturnsPagedNotifications() {
            Long userId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            Notification n1 = Notification.builder().id(10L).type(NotificationType.SUCCESSFUL_PAYMENT).content("Paid!").userId(userId).build();
            Notification n2 = Notification.builder().id(11L).type(NotificationType.MOVIE_REMINDER).content("Reminder!").userId(userId).build();
            Page<Notification> page = new PageImpl<>(List.of(n1, n2), pageable, 2);

            when(notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable)).thenReturn(page);

            Page<NotificationDTO> result = notificationService.getMyNotifications(userId, pageable);

            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
            assertEquals(10L, result.getContent().getFirst().id());
            verify(notificationRepository, times(1)).findByUserIdOrderByCreatedDateDesc(userId, pageable);
        }
    }

    @Nested
    @DisplayName("Tests for getNotification")
    class GetNotificationTests {
        @Test
        void getNotification_ExistingId_ReturnsNotificationDTO() {
            Long notificationId = 1L;
            Notification notification = Notification.builder()
                    .id(notificationId).type(NotificationType.REGISTERED_FROM_ANOTHER_DEVICE)
                    .content("New login detected").userId(42L).build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            NotificationDTO result = notificationService.getNotification(notificationId);

            assertNotNull(result);
            assertEquals(notificationId, result.id());
            assertEquals("New login detected", result.content());
            assertEquals(42L, result.userId());
        }

        @Test
        void getNotification_NonExistingId_ThrowsNotFoundException() {
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
            NotFoundException ex = assertThrows(NotFoundException.class, () -> notificationService.getNotification(notificationId));
            assertEquals("Notification not found with id: " + notificationId, ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Tests for markAsSent")
    class MarkAsSentTests {
        @Test
        void markAsSent_ExistingId_UpdatesSentDateAndReturnsDTO() {
            Long notificationId = 1L;
            Notification notification = Notification.builder()
                    .id(notificationId).type(NotificationType.EMAIL_VERIFICATION)
                    .content("Verify your account").userId(42L).sentDate(null).build();

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

            NotificationDTO result = notificationService.markAsSent(notificationId);

            assertNotNull(result);
            assertNotNull(result.sentDate());
            verify(notificationRepository, times(1)).save(notification);
        }

        @Test
        void markAsSent_NonExistingId_ThrowsNotFoundException() {
            Long notificationId = 999L;
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());
            NotFoundException ex = assertThrows(NotFoundException.class, () -> notificationService.markAsSent(notificationId));
            assertEquals("Notification not found with id: " + notificationId, ex.getMessage());
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }
}
```

- [ ] **Step 5: Write the rewritten `NotificationSchedulerTest`** (snapshot-based, booking-local)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/schedulers/NotificationSchedulerTest.java`:

```java
package com.awbd.cinema.schedulers;

import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.Order;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationScheduler notificationScheduler;

    @Captor private ArgumentCaptor<Notification> notificationCaptor;

    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        tomorrow = LocalDate.now().plusDays(1);
    }

    @Test
    void sendMovieReminders_ShouldDoNothing_WhenNoBookedTicketsForTomorrow() {
        when(ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow)).thenReturn(Collections.emptyList());

        notificationScheduler.sendMovieReminders();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendMovieReminders_ShouldSendOneNotificationPerUserPerSession() {
        Order orderAlice = Order.builder().id(50L).userId(1L).build();
        Order orderBob = Order.builder().id(51L).userId(2L).build();

        Ticket ticketAlice1 = Ticket.builder().id(101L).order(orderAlice).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();
        Ticket ticketAlice2 = Ticket.builder().id(102L).order(orderAlice).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();
        Ticket ticketBob = Ticket.builder().id(103L).order(orderBob).screenSessionId(99L)
                .movieTitle("Interstellar").sessionStartTime(LocalTime.of(20, 0)).build();

        when(ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow))
                .thenReturn(List.of(ticketAlice1, ticketAlice2, ticketBob));

        notificationScheduler.sendMovieReminders();

        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        List<Notification> saved = notificationCaptor.getAllValues();
        assertThat(saved).hasSize(2);

        Notification alice = saved.stream().filter(n -> n.getUserId().equals(1L)).findFirst().orElseThrow();
        assertThat(alice.getType()).isEqualTo(NotificationType.MOVIE_REMINDER);
        assertThat(alice.getOrder()).isEqualTo(orderAlice);
        assertThat(alice.getContent())
                .isEqualTo("Reminder: you have a ticket for \"Interstellar\" tomorrow at 20:00. Enjoy the show!");
        assertThat(alice.getCreatedDate()).isNotNull();
        assertThat(alice.getSentDate()).isNotNull();

        Notification bob = saved.stream().filter(n -> n.getUserId().equals(2L)).findFirst().orElseThrow();
        assertThat(bob.getOrder()).isEqualTo(orderBob);
    }
}
```

- [ ] **Step 6: Run the service-layer tests**

From `microservices/` run: `mvn -pl booking-service -am test -Dtest='TicketServiceTest,OrderServiceTest,NotificationServiceTest,NotificationSchedulerTest,TicketInfoServiceTest,OfferServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all PASS. (The `-Dsurefire.failIfNoSpecifiedTests=false` flag is needed because the same `-Dtest` pattern is applied to the `common` module too, which has none of these.)

- [ ] **Step 7: Commit**

```bash
git add microservices/booking-service/src/test/java/com/awbd/cinema/services microservices/booking-service/src/test/java/com/awbd/cinema/schedulers
git commit -m "Add booking-service service-layer unit tests (Feign + denormalization rewrites)"
```

---

### Task 8: `booking-service` — controller tests, integration test, full reactor build

Port the controller tests (one needs the `CustomUserDetails` constructor adaptation), rewrite the order integration test to be booking-local (seed tickets directly, mock the user Feign client), then build the whole 4-module reactor.

**Files:**
- Create: `src/test/resources/application.properties`
- Create (adapted): `controllers/BaseControllerTest.java`
- Copy (verbatim): `controllers/{OrderControllerTest,TicketControllerTest,TicketInfoControllerTest,OfferControllerTest}.java`
- Create (port + edit): `controllers/NotificationControllerTest.java`
- Create (rewritten): `OrderIntegrationTest.java`

- [ ] **Step 1: Create the default-profile test properties** (for the `@WebMvcTest` controller tests)

Create `microservices/booking-service/src/test/resources/application.properties`:

```properties
spring.application.name=booking-service
server.servlet.context-path=/api/v1

jwt.secret.key=redacted

auth.cookie.secure=false
auth.cookie.same-site=Lax

security.csrf.enabled=true
security.website.domain=http://localhost:4200
security.cors.allowed-origins=${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}

services.catalog.url=http://localhost:8082/api/v1
services.user.url=http://localhost:8081/api/v1
```

- [ ] **Step 2: Create the adapted `BaseControllerTest`** (validator-only SecurityConfig; mocks only the filter + CORS props)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java`:

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

- [ ] **Step 3: Port the 4 verbatim controller tests**

```bash
New-Item -ItemType Directory -Force -Path microservices/booking-service/src/test/java/com/awbd/cinema/controllers

Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/OrderControllerTest.java microservices/booking-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/TicketControllerTest.java microservices/booking-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/TicketInfoControllerTest.java microservices/booking-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/OfferControllerTest.java microservices/booking-service/src/test/java/com/awbd/cinema/controllers/
```

> Verified: these four only use `loginAs`/`loginAsDefaultUser` + `@MockitoBean` their own service + the booking DTOs (whose record shapes are unchanged). No cross-domain entity references.

- [ ] **Step 4: Port + adapt `NotificationControllerTest`**

Copy it, then make the `CustomUserDetails` constructor edit (it's the only booking controller test that built a `User` entity).

```bash
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/NotificationControllerTest.java microservices/booking-service/src/test/java/com/awbd/cinema/controllers/
```

Then in `microservices/booking-service/src/test/java/com/awbd/cinema/controllers/NotificationControllerTest.java`:

(a) Delete the import line:

```java
import com.awbd.cinema.entities.User;
```

(b) Replace this block:

```java
            User u = User.builder()
                    .id(mockUserId)
                    .username("test_user")
                    .password("password123")
                    .role(Role.USER)
                    .build();

            CustomUserDetails mockUserDetails = new CustomUserDetails(u);
```

with:

```java
            CustomUserDetails mockUserDetails = new CustomUserDetails(mockUserId, "test_user", "password123", Role.USER, null);
```

> Everything else in the file (the `Role` import, `NotificationDTO` usage, service mock) is unchanged and resolves in booking-service.

- [ ] **Step 5: Rewrite `OrderIntegrationTest`** (booking-local: seed tickets directly, mock the user Feign client)

Create `microservices/booking-service/src/test/java/com/awbd/cinema/OrderIntegrationTest.java`:

```java
package com.awbd.cinema;

import com.awbd.cinema.DTOs.OrderDTOs.CreateOrderDTO;
import com.awbd.cinema.DTOs.OrderDTOs.OrderItemDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.clients.UserServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.enums.TicketType;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketInfoRepository ticketInfoRepository;

    @MockitoBean private UserServiceClient userServiceClient;

    private final Long userId = 1L;
    private CustomUserDetails principal;
    private Ticket availableTicket;

    private Ticket seedTicket(Long seatId) {
        return ticketRepository.save(Ticket.builder()
                .isAvailable(true)
                .seatId(seatId).roomId(10L).screenSessionId(20L)
                .seatRow(1).seatNumber(seatId.intValue()).seatZone("A")
                .extraFee(BigDecimal.ZERO).extraPoints(0).sessionPoints(0)
                .movieTitle("Integration Test Movie")
                .sessionDate(LocalDate.now().plusDays(7))
                .sessionStartTime(LocalTime.of(18, 0))
                .build());
    }

    @BeforeEach
    void setUp() {
        principal = new CustomUserDetails(userId, "order_user", "password", Role.USER, null);
        availableTicket = seedTicket(1L);
        ticketInfoRepository.save(TicketInfo.builder().type(TicketType.ADULT).price(BigDecimal.valueOf(15.00)).build());

        when(userServiceClient.getLoyaltyPoints(anyLong())).thenReturn(new LoyaltyPointsDTO(userId, 0));
        when(userServiceClient.updateLoyaltyPoints(anyLong(), any())).thenReturn(new LoyaltyPointsDTO(userId, 0));
    }

    @Test
    @DisplayName("End-to-End: Create, Retrieve, Pay, and Cancel an Order (booking-local)")
    void testOrderFlow() throws Exception {
        OrderItemDTO orderItem = new OrderItemDTO(availableTicket.getId(), TicketType.ADULT);
        CreateOrderDTO createOrderDTO = new CreateOrderDTO(List.of(orderItem), false);

        String createResp = mockMvc.perform(post("/orders")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(15.00))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/tickets/{id}", availableTicket.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));

        mockMvc.perform(get("/orders/{id}", orderId).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/orders/{id}/pay", orderId).with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/orders/my").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));

        mockMvc.perform(patch("/orders/{id}/cancel", orderId).with(user(principal)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Paid orders cannot be cancelled."));

        Ticket anotherTicket = seedTicket(2L);
        OrderItemDTO anotherItem = new OrderItemDTO(anotherTicket.getId(), TicketType.ADULT);
        CreateOrderDTO anotherOrder = new CreateOrderDTO(List.of(anotherItem), false);

        String anotherResp = mockMvc.perform(post("/orders")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anotherOrder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long pendingOrderId = objectMapper.readTree(anotherResp).get("id").asLong();

        mockMvc.perform(patch("/orders/{id}/cancel", pendingOrderId).with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/tickets/{id}", anotherTicket.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));

        mockMvc.perform(delete("/orders/{id}", orderId).with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orders/{id}", orderId).with(user(principal)))
                .andExpect(status().isNotFound());
    }
}
```

> Notes: under `@ActiveProfiles("test")` Redis is suppressed (`RedisConfig` `@Profile("!test")`), so caching is a no-op and no Redis is needed. `@EnableScheduling`'s cron job (`0 0 9 …`) won't fire during the test. `CatalogServiceClient` is never called (tickets are seeded directly, not via `POST /tickets`), and `UserServiceClient` is mocked. `createOrder` runs with `useDiscount=false`, so the loyalty client isn't even invoked there; `payOrder` invokes the mocked client.

- [ ] **Step 6: Build the entire reactor (self-contained — no external DB/Redis/services)**

From `microservices/` run: `mvn clean verify`
Expected: `BUILD SUCCESS` for all four modules. Test totals — `common`: 10; `user-service`: 57; `catalog-service`: 121; `booking-service`: the ported `TicketInfoServiceTest`/`OfferServiceTest` + rewritten `TicketServiceTest`/`OrderServiceTest`/`NotificationServiceTest`/`NotificationSchedulerTest` + the 5 controller tests + the H2-backed `OrderIntegrationTest`, all green.

(Maven 3.9.11 + Java 21 on PATH as `mvn`; no `mvnw` inside `microservices/`.)

- [ ] **Step 7: Commit**

```bash
git add microservices/booking-service/src/test
git commit -m "Port and adapt booking-service controller/integration tests; full reactor builds green"
```

---

## Phase 3 Done — What Exists Now

- The `microservices/` reactor now has all four runnable services: `common`, `user-service` (`:8081`), `catalog-service` (`:8082`), `booking-service` (`:8083`).
- The **3-way Feign graph** is complete: booking→catalog (ticket-setup ①), booking↔user (loyalty points ②), user→booking (registration notification ③ — server side here, client side from Phase 1), each with a Resilience4j circuit breaker + fallback.
- All cross-service JPA joins are gone: `Order`/`Notification` carry `userId`; `Ticket` carries plain catalog IDs + a denormalized pricing/display snapshot, making `createOrder` and the movie-reminder scheduler fully booking-local.
- Whole ported + rewritten test suite green on H2 (no external DB/Redis/services needed for the build).

**Deferred to Phase 4 (not gaps):** the API gateway (Spring Cloud Gateway static routes + rate limiting + request/response logging filter), `docker-compose.microservices.yml` (per-service Postgres + shared Redis + all 4 services + gateway + client), `.env.example` DB-name vars, Actuator-based container healthchecks, and end-to-end multi-service run.

