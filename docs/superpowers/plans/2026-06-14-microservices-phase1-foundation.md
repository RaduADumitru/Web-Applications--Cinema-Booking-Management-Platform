# Microservices Phase 1: Foundation (`common` + `user-service`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the new `microservices/` Maven reactor with a shared `common` library and a fully working `user-service` (auth, registration, profile, JWT issuance with embedded `userId`/`role` claims, internal loyalty-points endpoints for a future booking-service), ported from the existing `cinema` monolith without touching it.

**Architecture:** Multi-module Maven reactor (`microservices/pom.xml`, packaging `pom`) with two modules for this phase: `common` (shared library jar — exceptions, `RestPage`, JWT utilities, the new **stateless** claims-based `JwtAuthenticationFilter`, CSRF filter, role hierarchy, Feign auth-propagation interceptor, shared DTOs) and `user-service` (Spring Boot app, its own `User` entity/table, auth + profile endpoints, plus new `/internal/users/{id}/loyalty-points` endpoints and a Feign call to a future booking-service for registration notifications). Every module reuses the monolith's base package `com.awbd.cinema` and identical sub-package names, so most files are byte-for-byte ports via `Copy-Item` — only the JWT/security layer, `AuthService`, and `UserService` get real changes.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2 (OpenFeign + Resilience4j circuit breaker), Java 21, Spring Data JPA, Spring Security, PostgreSQL (existing `cinema` DB, reused for Phase 1), Caffeine (login-attempt cache), jjwt 0.13.0, Lombok, dotenv-java 3.2.0, JUnit 5 / Mockito / AssertJ.

---

## Background & Key Design Decisions

These decisions were made during research and apply across all tasks below:

1. **Package structure is identical to the monolith.** Every class keeps its exact `com.awbd.cinema.*` package. This lets "unchanged" files be ported with plain `Copy-Item` (no import rewrites), and lets each service's `@SpringBootApplication` class (at the `com.awbd.cinema` root) component-scan both its own code and the `common` jar's beans.

2. **`common` holds the *stateless* JWT layer.** `JwtAuthenticationFilter` no longer does a DB lookup (`CustomUserDetailsService`/`UserDetailsService`). Instead, `JwtUtil.generateToken()` now embeds `userId` and `role` claims (in addition to the existing `sub`/`typ`), and the filter builds the `Authentication` directly from those claims.

3. **`CustomUserDetails` moves to `common` with a new primitive-field constructor** `CustomUserDetails(Long id, String username, String password, Role role, LocalDateTime emailVerifiedAt)` — it no longer takes a `User` entity (which `common` can't depend on). The stateless filter uses this constructor with claims from the token (`password=""`, `emailVerifiedAt=null` — neither is used after authentication). `user-service`'s `CustomUserDetailsService` (used only during `/auth/login`) is adapted with a **one-line change** to call this same constructor by passing the loaded `User`'s fields. This keeps `@AuthenticationPrincipal CustomUserDetails` working unchanged in `UserController`/`AuthController`.

4. **Registration notification becomes a Feign call.** `AuthServiceImpl.register()` no longer writes to a `NotificationRepository` (user-service has no `Notification` entity/table). It calls a new `NotificationServiceClient` Feign interface (`POST /internal/notifications` on a future booking-service), using the shared `CreateNotificationDTO(type, content, userId)`. A Feign fallback bean logs a warning and continues — registration succeeds even though booking-service doesn't exist yet in Phase 1 (this is the intended, spec'd resilience behaviour, not a Phase-1 gap).

5. **Loyalty points get a small internal API.** Two new methods on `UserService`/`UserServiceImpl` (`getLoyaltyPoints`, `updateLoyaltyPoints`) and a new `InternalUserController` exposing `GET`/`PATCH /internal/users/{id}/loyalty-points`, using new shared DTOs `LoyaltyPointsDTO(userId, loyaltyPoints)` and `AdjustLoyaltyPointsDTO(loyaltyPoints)` (PATCH sets the absolute new value — booking-service will GET first, compute, then PATCH). `/internal/**` is `permitAll`.

6. **Phase 1 reuses the existing local Postgres** (`docker-compose.yml`'s `postgres` service / `localhost:5432` / db `cinema`) via the same env vars (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET_KEY`, etc. — all already in `.env`). `ddl-auto=update` and the ported `User` entity (`@Table(name="users")`, minus its two `@OneToMany` relations) are schema-compatible with the existing `users` table. New per-service DB containers/vars are deferred to Phase 4.

7. **`RoleHierarchyConfig`** (new, in `common`) extracts the `roleHierarchy()`/`methodSecurityExpressionHandler()` beans out of the monolith's `SecurityConfig` so every service gets `ROLE_OWNER > ROLE_STAFF > ROLE_USER` for free via component scanning.

8. **`FeignAuthInterceptor`** (new, in `common`) forwards the incoming request's `Cookie` header onto every outgoing Feign call, so downstream services see the same JWT and apply their own `JwtAuthenticationFilter`/`@PreAuthorize` normally.

---

### Task 1: Parent reactor POM

**Files:**
- Create: `microservices/pom.xml`

- [ ] **Step 1: Create the parent reactor POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.7</version>
        <relativePath/>
    </parent>

    <groupId>com.awbd</groupId>
    <artifactId>microservices</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>microservices</name>
    <description>Cinema booking platform - microservices reactor</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.1.2</spring-cloud.version>
    </properties>

    <modules>
        <module>common</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>testCompile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Commit**

```bash
git add microservices/pom.xml
git commit -m "Add microservices Maven reactor parent POM"
```

---

### Task 2: `common` module skeleton + simple ported files

**Files:**
- Create: `microservices/common/pom.xml`
- Copy (via `Copy-Item`, identical package/content): `Role`, `NotificationType` enums; 6 exceptions + `GlobalExceptionHandler`; `RestPage`, `SecurityCorsProperties`; `CsrfCookieFilter`; `CreateNotificationDTO`

- [ ] **Step 1: Create `microservices/common/pom.xml`**

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

    <artifactId>common</artifactId>
    <packaging>jar</packaging>
    <name>common</name>
    <description>Shared library for cinema microservices</description>

    <dependencies>
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
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
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
    </dependencies>
</project>
```

- [ ] **Step 2: Port the enums, exceptions, `GlobalExceptionHandler`, `RestPage`, `SecurityCorsProperties`, `CsrfCookieFilter`, `CreateNotificationDTO` verbatim**

These files are byte-for-byte identical to the monolith (same package `com.awbd.cinema.*`). Run from the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/common/src/main/java/com/awbd/cinema/enums, microservices/common/src/main/java/com/awbd/cinema/exceptions, microservices/common/src/main/java/com/awbd/cinema/utils, microservices/common/src/main/java/com/awbd/cinema/security, microservices/common/src/main/java/com/awbd/cinema/config, microservices/common/src/main/java/com/awbd/cinema/DTOs/NotificationDTOs, microservices/common/src/main/java/com/awbd/cinema/DTOs/UserDTOs

Copy-Item cinema/src/main/java/com/awbd/cinema/enums/Role.java microservices/common/src/main/java/com/awbd/cinema/enums/
Copy-Item cinema/src/main/java/com/awbd/cinema/enums/NotificationType.java microservices/common/src/main/java/com/awbd/cinema/enums/

Copy-Item cinema/src/main/java/com/awbd/cinema/exceptions/*.java microservices/common/src/main/java/com/awbd/cinema/exceptions/

Copy-Item cinema/src/main/java/com/awbd/cinema/utils/RestPage.java microservices/common/src/main/java/com/awbd/cinema/utils/
Copy-Item cinema/src/main/java/com/awbd/cinema/utils/SecurityCorsProperties.java microservices/common/src/main/java/com/awbd/cinema/utils/
Copy-Item cinema/src/main/java/com/awbd/cinema/utils/GlobalExceptionHandler.java microservices/common/src/main/java/com/awbd/cinema/utils/

Copy-Item cinema/src/main/java/com/awbd/cinema/security/CsrfCookieFilter.java microservices/common/src/main/java/com/awbd/cinema/security/

Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/NotificationDTOs/CreateNotificationDTO.java microservices/common/src/main/java/com/awbd/cinema/DTOs/NotificationDTOs/
```

This copies: `enums/Role.java`, `enums/NotificationType.java`, `exceptions/AlreadyExistsException.java`, `exceptions/BadRequestException.java`, `exceptions/InvalidFieldException.java`, `exceptions/NotFoundException.java`, `exceptions/TooManyRequestsException.java`, `exceptions/UnauthenticatedException.java`, `utils/RestPage.java`, `utils/SecurityCorsProperties.java`, `utils/GlobalExceptionHandler.java`, `security/CsrfCookieFilter.java`, `DTOs/NotificationDTOs/CreateNotificationDTO.java`.

- [ ] **Step 3: Create the two new loyalty-points DTOs**

Create `microservices/common/src/main/java/com/awbd/cinema/DTOs/UserDTOs/LoyaltyPointsDTO.java`:

```java
package com.awbd.cinema.DTOs.UserDTOs;

public record LoyaltyPointsDTO(
        Long userId,
        Integer loyaltyPoints
) {
}
```

Create `microservices/common/src/main/java/com/awbd/cinema/DTOs/UserDTOs/AdjustLoyaltyPointsDTO.java`:

```java
package com.awbd.cinema.DTOs.UserDTOs;

public record AdjustLoyaltyPointsDTO(
        Integer loyaltyPoints
) {
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `cd microservices ; ./mvnw -pl common compile`

(If `mvnw` isn't present yet, generate wrapper scripts first: `mvn -N io.takari:maven:wrapper -Dmaven=3.9.9` from `microservices/`, or invoke `mvn` directly if it's on `PATH`.)

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add microservices/common
git commit -m "Add common module: ported enums, exceptions, RestPage, CORS/CSRF helpers, and new loyalty-points DTOs"
```

---

### Task 3: `common` — `JwtUtil` rewrite (userId + role claims)

The monolith's `JwtUtil.generateToken(username)` only embeds `sub`+`typ`. The stateless filter needs `userId` and `role` in the token so it can build `Authentication` without a DB lookup. We add those claims to access tokens, add `extractUserId`/`extractRole` accessors, and **drop** the unused no-arg `extractUsername()` (a SecurityContext convenience method with zero production callers — confirmed via grep; only a test referenced it). `generateRefreshToken` stays username-only (the refresh flow re-loads the user from DB to re-mint an access token with fresh claims).

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/utils/JwtUtil.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/utils/JwtUtilTest.java`

- [ ] **Step 1: Write the new `JwtUtil`**

Create `microservices/common/src/main/java/com/awbd/cinema/utils/JwtUtil.java`:

```java
package com.awbd.cinema.utils;

import com.awbd.cinema.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtUtil {

    @Value("${jwt.secret.key}")
    private String secretKey;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("typ", "ACCESS")
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(3, ChronoUnit.HOURS)))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("typ", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(30, ChronoUnit.DAYS)))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Role extractRole(String token) {
        String role = extractClaim(token, claims -> claims.get("role", String.class));
        return role == null ? null : Role.valueOf(role);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims =
                Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, String username) {
        return username.equals(extractUsername(token)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
```

- [ ] **Step 2: Write the rewritten `JwtUtilTest`**

Create `microservices/common/src/test/java/com/awbd/cinema/utils/JwtUtilTest.java`. This rewrites the monolith test: all `generateToken` calls use the new 3-arg signature, asserts `extractUserId`/`extractRole`, and drops the `extractUsername_FromSecurityContext` test (method removed).

```java
package com.awbd.cinema.utils;

import com.awbd.cinema.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // HS256 algorithms require a secret key of at least 256 bits (32 bytes)
    private final String testSecretKey = "superSecretKeyForCinemaApplication2026!!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", testSecretKey);
    }

    @Test
    void generateToken_ShouldGenerateValidAccessTokenWithClaims() {
        String username = "cinema_fan";

        String token = jwtUtil.generateToken(username, 42L, Role.USER);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractRole(token)).isEqualTo(Role.USER);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());

        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("ACCESS");
    }

    @Test
    void generateRefreshToken_ShouldGenerateValidRefreshToken() {
        String username = "admin_user";

        String token = jwtUtil.generateRefreshToken(username);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());

        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("REFRESH");
    }

    @Test
    void isTokenValid_ShouldReturnTrue_WhenUsernameMatchesAndTokenNotExpired() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username, 1L, Role.USER);

        boolean isValid = jwtUtil.isTokenValid(token, username);

        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username, 1L, Role.USER);

        boolean isValid = jwtUtil.isTokenValid(token, "intruderUser");

        assertThat(isValid).isFalse();
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd microservices ; ./mvnw -pl common test -Dtest=JwtUtilTest`
Expected: PASS (4 tests).

- [ ] **Step 4: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/utils/JwtUtil.java microservices/common/src/test/java/com/awbd/cinema/utils/JwtUtilTest.java
git commit -m "Add JwtUtil to common with userId/role claims"
```

---

### Task 4: `common` — `CustomUserDetails` (primitive constructor)

Moves `CustomUserDetails` into `common` and replaces the `User`-entity constructor (which `common` can't have — it has no entities) with a primitive-field constructor. Both the DB login path (`user-service`'s `CustomUserDetailsService`) and the stateless filter use this one constructor. The `getId()` and `getEmailVerifiedAt()` getters are preserved so `CustomAuthenticationProvider` and `@AuthenticationPrincipal` usage keep working.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/security/CustomUserDetails.java`

- [ ] **Step 1: Write `CustomUserDetails`**

Create `microservices/common/src/main/java/com/awbd/cinema/security/CustomUserDetails.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {
    @Getter private final Long id;
    private final String username;
    private final String password;
    @Getter private final LocalDateTime emailVerifiedAt;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String username, String password, Role role, LocalDateTime emailVerifiedAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.emailVerifiedAt = emailVerifiedAt;
        this.authorities =
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/security/CustomUserDetails.java
git commit -m "Add CustomUserDetails to common with primitive-field constructor"
```

---

### Task 5: `common` — stateless `JwtAuthenticationFilter`

Rewrite the filter to be **stateless**: no `CustomUserDetailsService`/DB. It reads the `jwt` cookie, requires `typ=ACCESS`, then builds a `CustomUserDetails` straight from the `userId`/`role`/`username` claims (`password=""`, `emailVerifiedAt=null` — unused post-auth) and sets the `Authentication`. Invalid/garbage tokens clear the cookie, exactly as before. This single filter (in `common`) is component-scanned by every service.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/security/JwtAuthenticationFilter.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/security/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: Write the stateless filter**

Create `microservices/common/src/main/java/com/awbd/cinema/security/JwtAuthenticationFilter.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.enums.Role;
import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Value("${auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${auth.cookie.same-site}")
    private String cookieSameSite;

    private void clearJwtCookie(HttpServletResponse response) {
        ResponseCookie cookie =
                ResponseCookie.from("jwt", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(0)
                        .sameSite(cookieSameSite)
                        .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        String token = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            try {
                String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
                if (!"ACCESS".equals(type)) {
                    chain.doFilter(request, response);
                    return;
                }
                String username = jwtUtil.extractUsername(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null
                        && jwtUtil.isTokenValid(token, username)) {
                    Long userId = jwtUtil.extractUserId(token);
                    Role role = jwtUtil.extractRole(token);
                    CustomUserDetails userDetails =
                            new CustomUserDetails(userId, username, "", role, null);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException e) {
                log.warn("Rejecting request with invalid JWT cookie: {}", e.getMessage());
                clearJwtCookie(response);
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Write the rewritten filter test**

Create `microservices/common/src/test/java/com/awbd/cinema/security/JwtAuthenticationFilterTest.java`. No `CustomUserDetailsService` mock; the valid-token test stubs `extractUserId`/`extractRole`/`isTokenValid` and asserts the principal is a claims-built `CustomUserDetails`. The `UsernameNotFoundException` scenario is removed (no DB lookup can fail anymore).

```java
package com.awbd.cinema.security;

import com.awbd.cinema.enums.Role;
import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "cookieSecure", true);
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "cookieSameSite", "Lax");
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenNoCookiesExist() throws ServletException, IOException {
        when(request.getCookies()).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenJwtCookieNotPresent() throws ServletException, IOException {
        Cookie otherCookie = new Cookie("theme", "dark");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldPassThroughEarly_WhenTokenIsNotAccessToken() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "refresh_token_value");
        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("refresh_token_value"), any())).thenReturn("REFRESH");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractUsername(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldAuthenticate_WhenTokenIsValidAndNoPriorAuthentication() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "valid_access_token");

        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("valid_access_token"), any())).thenReturn("ACCESS");
        when(jwtUtil.extractUsername("valid_access_token")).thenReturn("cinema_fan");
        when(jwtUtil.isTokenValid("valid_access_token", "cinema_fan")).thenReturn(true);
        when(jwtUtil.extractUserId("valid_access_token")).thenReturn(7L);
        when(jwtUtil.extractRole("valid_access_token")).thenReturn(Role.USER);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("cinema_fan");
        assertThat(auth.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) auth.getPrincipal()).getId()).isEqualTo(7L);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void doFilterInternal_ShouldNotReauthenticate_WhenAuthenticationAlreadyExists() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "valid_access_token");
        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("valid_access_token"), any())).thenReturn("ACCESS");
        when(jwtUtil.extractUsername("valid_access_token")).thenReturn("cinema_fan");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractUserId(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
    }

    @Test
    void doFilterInternal_ShouldClearCookie_WhenJwtExceptionIsThrown() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "malformed_or_expired_token");
        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("malformed_or_expired_token"), any())).thenThrow(new JwtException("Expired token"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd microservices ; ./mvnw -pl common test -Dtest=JwtAuthenticationFilterTest`
Expected: PASS (6 tests).

- [ ] **Step 4: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/security/JwtAuthenticationFilter.java microservices/common/src/test/java/com/awbd/cinema/security/JwtAuthenticationFilterTest.java
git commit -m "Add stateless claims-based JwtAuthenticationFilter to common"
```

---

### Task 6: `common` — `RoleHierarchyConfig` + `FeignAuthInterceptor`

Two new shared `@Configuration`/`@Component` beans. `RoleHierarchyConfig` extracts the `roleHierarchy()`/`methodSecurityExpressionHandler()` beans from the monolith's `SecurityConfig` so every service inherits `ROLE_OWNER > ROLE_STAFF > ROLE_USER`. `FeignAuthInterceptor` copies the incoming request's `Cookie` header onto outgoing Feign calls so the downstream service sees the same JWT.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/config/RoleHierarchyConfig.java`
- Create: `microservices/common/src/main/java/com/awbd/cinema/config/FeignAuthInterceptor.java`

- [ ] **Step 1: Write `RoleHierarchyConfig`**

Create `microservices/common/src/main/java/com/awbd/cinema/config/RoleHierarchyConfig.java`:

```java
package com.awbd.cinema.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_STAFF \n " +
                        "ROLE_STAFF > ROLE_USER"
        );
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
```

- [ ] **Step 2: Write `FeignAuthInterceptor`**

Create `microservices/common/src/main/java/com/awbd/cinema/config/FeignAuthInterceptor.java`:

```java
package com.awbd.cinema.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String cookie = request.getHeader(HttpHeaders.COOKIE);
            if (cookie != null && !cookie.isBlank()) {
                template.header(HttpHeaders.COOKIE, cookie);
            }
        }
    }
}
```

- [ ] **Step 3: Verify the whole `common` module compiles and tests pass**

Run: `cd microservices ; ./mvnw -pl common verify`
Expected: `BUILD SUCCESS` (JwtUtilTest + JwtAuthenticationFilterTest green).

- [ ] **Step 4: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/config
git commit -m "Add RoleHierarchyConfig and FeignAuthInterceptor to common"
```

---

### Task 7: `user-service` skeleton — POM, application class, properties, and ported files

Register `user-service` as the second reactor module, create its Spring Boot app + config, and port all the files that need **no logic change** (entity minus its two `@OneToMany` relations, repository, DTOs, validators, controllers, login-attempt service, startup listener).

**Files:**
- Modify: `microservices/pom.xml` (add `<module>user-service</module>`)
- Create: `microservices/user-service/pom.xml`
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/UserServiceApplication.java`
- Create: `microservices/user-service/src/main/resources/application.properties`
- Create: `microservices/user-service/src/test/resources/application.properties`
- Create (modified): `microservices/user-service/src/main/java/com/awbd/cinema/entities/User.java`
- Copy (verbatim): `UserRepository`; 6 AuthDTOs; 3 UserDTOs; `PasswordMatch`/`PasswordMatchValidator`; `AuthController`/`UserController`; `LoginAttemptService`/`LoginAttemptServiceImpl`; `StartupListener`

- [ ] **Step 1: Add the module to the parent reactor**

In `microservices/pom.xml`, change:

```xml
    <modules>
        <module>common</module>
    </modules>
```

to:

```xml
    <modules>
        <module>common</module>
        <module>user-service</module>
    </modules>
```

- [ ] **Step 2: Create `microservices/user-service/pom.xml`**

Drops the monolith's TMDB, Redis/Jedis, and springdoc deps (YAGNI for Phase 1). Adds a dependency on `common`. Keeps Caffeine (login-attempt cache), dotenv, postgres, jjwt, JPA/web/security/validation. The Spring Cloud OpenFeign + Resilience4j starters come transitively from `common` but are declared here too so Feign/circuit-breaker autoconfig is unambiguous.

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

    <artifactId>user-service</artifactId>
    <packaging>jar</packaging>
    <name>user-service</name>
    <description>Authentication and user management microservice</description>

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
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
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

- [ ] **Step 3: Create `UserServiceApplication`**

`@EnableFeignClients` enables the notification client. No `@EnableScheduling` (no scheduled jobs) and no `@EnableSpringDataWebSupport` (no endpoint returns a `Page`).

Create `microservices/user-service/src/main/java/com/awbd/cinema/UserServiceApplication.java`:

```java
package com.awbd.cinema;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class UserServiceApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `microservices/user-service/src/main/resources/application.properties`**

Phase 1 reuses the existing `cinema` DB and `.env`. Drops TMDB/Redis lines. Adds the service port (`8081`), the booking-service URL placeholder for the notification Feign client, and `spring.cloud.openfeign.circuitbreaker.enabled=true` so the `@FeignClient(fallback=...)` works.

```properties
spring.application.name=user-service

server.port=8081
server.servlet.context-path=/api/v1

logging.file.name=logs/user-service.log
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
security.max-attempts=${SECURITY_MAX_ATTEMPTS}
security.website.domain=${SECURITY_WEBSITE_DOMAIN}
security.cors.allowed-origins=${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

bootstrap.owner-username=${BOOTSTRAP_OWNER_USERNAME}
bootstrap.owner-password=${BOOTSTRAP_OWNER_PASSWORD}
bootstrap.owner-email=${BOOTSTRAP_OWNER_EMAIL}
bootstrap.owner-first-name=${BOOTSTRAP_OWNER_FIRST_NAME}
bootstrap.owner-last-name=${BOOTSTRAP_OWNER_LAST_NAME}
bootstrap.owner-phone-number=${BOOTSTRAP_OWNER_PHONE_NUMBER}

services.booking.url=${BOOKING_SERVICE_URL:http://localhost:8083/api/v1}

spring.cloud.openfeign.circuitbreaker.enabled=true

management.endpoints.web.exposure.include=health,info,metrics,prometheus

logging.level.com.awbd.cinema=DEBUG
```

- [ ] **Step 5: Create `microservices/user-service/src/test/resources/application.properties`**

Mirrors the monolith test props (literal values, H2-friendly via the runtime `h2` dep), drops TMDB/Redis, adds the Feign settings and the booking URL placeholder so `@SpringBootTest` context loads.

```properties
spring.application.name=user-service

server.servlet.context-path=/api/v1

spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update

spring.datasource.url=jdbc:postgresql://localhost:5432/cinema
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}

jwt.secret.key=redacted

auth.cookie.secure=false
auth.cookie.same-site=Lax

security.csrf.enabled=true
security.max-attempts=5
security.website.domain=http://localhost:4200
security.cors.allowed-origins=${SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:4200}

bootstrap.owner-username=${BOOTSTRAP_OWNER_USERNAME}
bootstrap.owner-password=${BOOTSTRAP_OWNER_PASSWORD}
bootstrap.owner-email=${BOOTSTRAP_OWNER_EMAIL}
bootstrap.owner-first-name=${BOOTSTRAP_OWNER_FIRST_NAME}
bootstrap.owner-last-name=${BOOTSTRAP_OWNER_LAST_NAME}
bootstrap.owner-phone-number=${BOOTSTRAP_OWNER_PHONE_NUMBER}

services.booking.url=http://localhost:8083/api/v1
spring.cloud.openfeign.circuitbreaker.enabled=true

logging.level.com.awbd.cinema=DEBUG
```

> Note: the monolith's `AuthIntegrationTest` runs under `@ActiveProfiles("test")` against this DB config. It is ported verbatim in Task 11.

- [ ] **Step 6: Port the unchanged files verbatim**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/user-service/src/main/java/com/awbd/cinema/entities, microservices/user-service/src/main/java/com/awbd/cinema/repositories, microservices/user-service/src/main/java/com/awbd/cinema/DTOs/AuthDTOs, microservices/user-service/src/main/java/com/awbd/cinema/DTOs/UserDTOs, microservices/user-service/src/main/java/com/awbd/cinema/validators, microservices/user-service/src/main/java/com/awbd/cinema/controllers, microservices/user-service/src/main/java/com/awbd/cinema/services/LoginAttemptService, microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService, microservices/user-service/src/main/java/com/awbd/cinema/services/UserService, microservices/user-service/src/main/java/com/awbd/cinema/security, microservices/user-service/src/main/java/com/awbd/cinema/clients, microservices/user-service/src/main/java/com/awbd/cinema/listeners

Copy-Item cinema/src/main/java/com/awbd/cinema/repositories/UserRepository.java microservices/user-service/src/main/java/com/awbd/cinema/repositories/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/AuthDTOs/*.java microservices/user-service/src/main/java/com/awbd/cinema/DTOs/AuthDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/UserDTOs/ProfileDTO.java microservices/user-service/src/main/java/com/awbd/cinema/DTOs/UserDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/UserDTOs/PromoteDTO.java microservices/user-service/src/main/java/com/awbd/cinema/DTOs/UserDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/DTOs/UserDTOs/UpdateProfileDTO.java microservices/user-service/src/main/java/com/awbd/cinema/DTOs/UserDTOs/
Copy-Item cinema/src/main/java/com/awbd/cinema/validators/PasswordMatch.java microservices/user-service/src/main/java/com/awbd/cinema/validators/
Copy-Item cinema/src/main/java/com/awbd/cinema/validators/PasswordMatchValidator.java microservices/user-service/src/main/java/com/awbd/cinema/validators/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/AuthController.java microservices/user-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/controllers/UserController.java microservices/user-service/src/main/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/main/java/com/awbd/cinema/services/LoginAttemptService/*.java microservices/user-service/src/main/java/com/awbd/cinema/services/LoginAttemptService/
Copy-Item cinema/src/main/java/com/awbd/cinema/listeners/StartupListener.java microservices/user-service/src/main/java/com/awbd/cinema/listeners/
```

> The `LoginResponseDTO`/`ProfileDTO` records call `User` getters that still exist on the trimmed entity, so they port unchanged. `StartupListener` only depends on `AuthService` + bootstrap properties, both present.

- [ ] **Step 7: Create the trimmed `User` entity**

Identical to the monolith's `User` **except** the two `@OneToMany` relations (`orders`, `notifications`) and the now-unused `java.util.List` import are removed — `user-service` has no `Order`/`Notification` entities. `@Table(name="users")` keeps it schema-compatible with the existing table.

Create `microservices/user-service/src/main/java/com/awbd/cinema/entities/User.java`:

```java
package com.awbd.cinema.entities;

import com.awbd.cinema.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "The username field is required.")
    private String username;

    @Column(name="first_name", nullable = false)
    @NotBlank(message = "Please include your first name.")
    @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Invalid first name.")
    private String firstName;

    @Column(name="last_name", nullable = false)
    @NotBlank(message = "Please include your last name.")
    @Pattern(regexp = "^[a-zA-ZăâîșțĂÂÎȘȚ]{2,}(?:[ -][a-zA-ZăâîșțĂÂÎȘȚ]+){0,2}$", message = "Invalid last name.")
    private String lastName;

    @Column(unique = true, nullable = false)
    @Email(message = "Email address is not correct.")
    @NotBlank(message = "Please include your email address.")
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name="phone_number", nullable = false)
    @NotBlank(message = "Please include your phone number.")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number is invalid.")
    private String phoneNumber;

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime deletedAt;

    @Column(name = "email_verified_at")
    @Builder.Default
    private LocalDateTime emailVerifiedAt = LocalDateTime.now(); //placeholder, it will actually be set via email verification in a future update
}
```

- [ ] **Step 8: Commit**

```bash
git add microservices/pom.xml microservices/user-service
git commit -m "Scaffold user-service module: pom, app, properties, ported entity/repo/DTOs/controllers"
```

---

### Task 8: `user-service` — security layer (`CustomUserDetailsService`, `CustomAuthenticationProvider`, `SecurityConfig`)

`CustomUserDetailsService` is the DB-backed login path — adapted by **one line** (the `CustomUserDetails` constructor now takes primitives). `CustomAuthenticationProvider` ports verbatim. `SecurityConfig` ports with two changes: remove the `roleHierarchy()`/`methodSecurityExpressionHandler()` beans (now in `common`'s `RoleHierarchyConfig`), and add `/internal/**` to `permitAll`.

**Files:**
- Create (adapted): `microservices/user-service/src/main/java/com/awbd/cinema/security/CustomUserDetailsService.java`
- Copy (verbatim): `microservices/user-service/src/main/java/com/awbd/cinema/security/CustomAuthenticationProvider.java`
- Create (adapted): `microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`

- [ ] **Step 1: Port `CustomAuthenticationProvider` verbatim**

```bash
Copy-Item cinema/src/main/java/com/awbd/cinema/security/CustomAuthenticationProvider.java microservices/user-service/src/main/java/com/awbd/cinema/security/
```

(It uses `CustomUserDetails.getEmailVerifiedAt()`, which the new `common` class still exposes — compiles unchanged.)

- [ ] **Step 2: Create the adapted `CustomUserDetailsService`**

Create `microservices/user-service/src/main/java/com/awbd/cinema/security/CustomUserDetailsService.java` — identical to the monolith except the final `return` line builds `CustomUserDetails` from the loaded `User`'s fields:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.entities.User;
import com.awbd.cinema.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(() -> new UsernameNotFoundException(username));

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.getEmailVerifiedAt());
    }
}
```

- [ ] **Step 3: Create the adapted `SecurityConfig`**

Create `microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`. Differences from the monolith: `/internal/**` added to `permitAll`, and the two role-hierarchy beans removed (they live in `common.config.RoleHierarchyConfig`).

```java
package com.awbd.cinema.security;

import com.awbd.cinema.exceptions.UnauthenticatedException;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.SecurityCorsProperties;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginAttemptService loginAttemptService;
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
                        .requestMatchers("/auth/**").permitAll()
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
                    .ignoringRequestMatchers("/auth/**", "/internal/**")
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
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new CustomAuthenticationProvider(
                userDetailsService,
                bCryptPasswordEncoder(),
                loginAttemptService
        );
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

- [ ] **Step 4: Commit**

```bash
git add microservices/user-service/src/main/java/com/awbd/cinema/security
git commit -m "Add user-service security layer (adapted UserDetailsService + SecurityConfig)"
```

---

### Task 9: `user-service` — notification Feign client + `AuthServiceImpl` rewrite

Replace the monolith's `notificationRepository.save(...)` in `register()` with a Feign call to the (future) booking-service `/internal/notifications`, guarded by a Feign fallback so registration still succeeds when booking-service is down/absent. Also update the cookie helpers to pass `userId`+`role` into the new `JwtUtil.generateToken`.

**Files:**
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClient.java`
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClientFallback.java`
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/AuthService.java`
- Create (rewritten): `microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/AuthServiceImpl.java`
- Test: `microservices/user-service/src/test/java/com/awbd/cinema/services/AuthServiceTest.java`

- [ ] **Step 1: Port the `AuthService` interface verbatim**

```bash
Copy-Item cinema/src/main/java/com/awbd/cinema/services/AuthService/AuthService.java microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/
```

- [ ] **Step 2: Create the Feign client**

Create `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClient.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "booking-service",
        url = "${services.booking.url}",
        fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping("/internal/notifications")
    void createNotification(@RequestBody CreateNotificationDTO dto);
}
```

- [ ] **Step 3: Create the fallback**

Create `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationServiceClientFallback.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void createNotification(CreateNotificationDTO dto) {
        log.warn("booking-service unavailable; skipping notification of type {} for user {}.",
                dto.type(), dto.userId());
    }
}
```

- [ ] **Step 4: Create the rewritten `AuthServiceImpl`**

Create `microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/AuthServiceImpl.java`. Changes vs monolith: `NotificationRepository` → `NotificationServiceClient`; `register()` builds a `CreateNotificationDTO` and calls the client; cookie helpers take `userId`+`role`.

```java
package com.awbd.cinema.services.AuthService;

import com.awbd.cinema.DTOs.AuthDTOs.*;
import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.clients.NotificationServiceClient;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${auth.cookie.same-site}")
    private String cookieSameSite;

    @Override
    @Transactional
    public RegisterResponseDTO register(RegisterDTO register) {
        validateUserUniqueness(register);

        User u = maptoEntity(register);

        User savedUser = userRepository.save(u);

        CreateNotificationDTO notification = new CreateNotificationDTO(
                NotificationType.EMAIL_VERIFICATION,
                "Welcome, " + savedUser.getFirstName() + "! Please verify your email address ("
                        + savedUser.getEmail() + ") to activate your account.",
                savedUser.getId());
        notificationServiceClient.createNotification(notification);

        return new RegisterResponseDTO("Account created successfully.", savedUser.getUsername());
    }

    @Override
    public LoginActionDTO login(LoginDTO login) {
        if (loginAttemptService.isBlocked(login.username())) {
            log.warn("Blocked login attempt for user '{}': too many failed attempts.", login.username());
            throw new TooManyRequestsException("Too many attempts. Try again later.");
        }
        Authentication auth;
        try {
            auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(login.username(), login.password()));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for user '{}': invalid credentials.", login.username());
            loginAttemptService.loginFailed(login.username());
            throw new InvalidFieldException("Invalid account details.");
        } catch (LockedException e) {
            log.warn("Login attempt for locked account '{}'.", login.username());
            throw new TooManyRequestsException("Too many attempts. Try again later.");
        } catch (DisabledException e) {
            log.warn("Login attempt for unverified account '{}'.", login.username());
            throw new InvalidFieldException("Email is not verified.");
        }

        User u =
                userRepository
                        .findByUsernameIgnoreCase(auth.getName())
                        .orElseThrow(() -> new InvalidFieldException("Invalid account details."));

        if (u.getDeletedAt() != null) {
            log.warn("Login attempt for deleted account '{}'.", login.username());
            loginAttemptService.loginFailed(login.username());
            throw new InvalidFieldException("Invalid account details.");
        }

        loginAttemptService.loginSucceeded(login.username());

        ResponseCookie jwtCookie = createJwtCookie(u.getUsername(), u.getId(), u.getRole());
        ResponseCookie refreshCookie = createRefreshCookie(u.getUsername());

        return new LoginActionDTO(LoginResponseDTO.from(u), new LoginCookiesDTO(jwtCookie, refreshCookie));
    }

    @Override
    public void createOwner(RegisterDTO owner) {
        userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(owner.username(), owner.email())
                .orElseGet(() -> {
                    User u = maptoEntity(owner);
                    u.setRole(Role.OWNER);

                    log.info("Created owner: {}", u.getUsername());
                    return userRepository.save(u);
                });
    }

    private void validateUserUniqueness(RegisterDTO register) {
        if (userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(register.username(), register.email())) {
            throw new AlreadyExistsException("Username or email address is already in use.");
        }
    }

    private User maptoEntity(RegisterDTO dto) {
        return User.builder()
                .username(dto.username())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .firstName(HtmlUtils.htmlEscape(dto.firstName()))
                .lastName(HtmlUtils.htmlEscape(dto.lastName()))
                .phoneNumber(dto.phoneNumber())
                .build();
    }

    private ResponseCookie createJwtCookie(String username, Long userId, Role role) {
        String token = jwtUtil.generateToken(username, userId, role);
        return ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(3 * 60 * 60) // 3 hours
                .sameSite(cookieSameSite)
                .build();
    }

    private ResponseCookie createRefreshCookie(String username) {
        String token = jwtUtil.generateRefreshToken(username);
        return ResponseCookie.from("refresh", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(60L * 60L * 24L * 30L) // 30 days
                .sameSite(cookieSameSite)
                .build();
    }
}
```

- [ ] **Step 5: Create the rewritten `AuthServiceTest`**

Create `microservices/user-service/src/test/java/com/awbd/cinema/services/AuthServiceTest.java`. Changes vs monolith: mock `NotificationServiceClient` instead of `NotificationRepository`; `register_Success` captures the `CreateNotificationDTO`; `savedUser`/`activeUser` builders get `.id(...)`; `login_Success` stubs the 3-arg `generateToken`.

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.AuthDTOs.LoginActionDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterResponseDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.clients.NotificationServiceClient;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.AuthService.AuthServiceImpl;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationServiceClient notificationServiceClient;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthServiceImpl authService;

    private RegisterDTO sampleRegisterDTO;
    private LoginDTO sampleLoginDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "cookieSecure", true);
        ReflectionTestUtils.setField(authService, "cookieSameSite", "Strict");

        sampleRegisterDTO = new RegisterDTO(
                "testuser", "Password123!", "Password123!",
                "test@example.com", "John", "Doe", "+1234567890"
        );

        sampleLoginDTO = new LoginDTO("testuser", "Password123!");
    }

    @Nested
    class RegisterTests {

        @Test
        void register_Success() {
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(anyString(), anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedUser = User.builder()
                    .id(10L)
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            RegisterResponseDTO response = authService.register(sampleRegisterDTO);

            assertNotNull(response);
            assertEquals("Account created successfully.", response.message());
            assertEquals("testuser", response.username());

            ArgumentCaptor<CreateNotificationDTO> notificationCaptor = ArgumentCaptor.forClass(CreateNotificationDTO.class);
            verify(notificationServiceClient, times(1)).createNotification(notificationCaptor.capture());

            CreateNotificationDTO sentNotification = notificationCaptor.getValue();
            assertEquals(NotificationType.EMAIL_VERIFICATION, sentNotification.type());
            assertEquals(10L, sentNotification.userId());
            assertTrue(sentNotification.content().contains("test@example.com"));
        }

        @Test
        void register_ThrowsAlreadyExistsException_WhenUserOrEmailExists() {
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(true);

            assertThrows(AlreadyExistsException.class, () -> authService.register(sampleRegisterDTO));
            verify(userRepository, never()).save(any(User.class));
            verify(notificationServiceClient, never()).createNotification(any(CreateNotificationDTO.class));
        }
    }

    @Nested
    class LoginTests {

        @Test
        void login_ThrowsTooManyRequestsException_WhenBlocked() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(true);

            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        void login_ThrowsInvalidFieldException_OnBadCredentials() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_ThrowsTooManyRequestsException_OnLockedException() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new LockedException("Locked"));

            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_OnDisabledException() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new DisabledException("Disabled"));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserNotFoundInDatabase() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserIsSoftDeleted() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User softDeletedUser = User.builder()
                    .username("testuser")
                    .deletedAt(LocalDateTime.now())
                    .build();

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(softDeletedUser));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_Success() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User activeUser = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .role(Role.USER)
                    .deletedAt(null)
                    .build();

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(activeUser));

            when(jwtUtil.generateToken("testuser", 1L, Role.USER)).thenReturn("mock-jwt-token");
            when(jwtUtil.generateRefreshToken("testuser")).thenReturn("mock-refresh-token");

            LoginActionDTO result = authService.login(sampleLoginDTO);

            assertNotNull(result);
            assertEquals("testuser", result.response().username());

            ResponseCookie jwtCookie = result.cookies().jwtCookie();
            assertEquals("jwt", jwtCookie.getName());
            assertEquals("mock-jwt-token", jwtCookie.getValue());
            assertTrue(jwtCookie.isSecure());
            assertTrue(jwtCookie.isHttpOnly());
            assertEquals("Strict", jwtCookie.getSameSite());

            ResponseCookie refreshCookie = result.cookies().refreshTokenCookie();
            assertEquals("refresh", refreshCookie.getName());
            assertEquals("mock-refresh-token", refreshCookie.getValue());

            verify(loginAttemptService, times(1)).loginSucceeded("testuser");
        }
    }

    @Nested
    class CreateOwnerTests {

        @Test
        void createOwner_DoesNotSave_IfOwnerAlreadyExists() {
            User existingUser = User.builder().username("testuser").email("test@example.com").build();
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.of(existingUser));

            authService.createOwner(sampleRegisterDTO);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void createOwner_SavesNewUserAsOwner_IfNotFound() {
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedOwner = User.builder()
                    .username("testuser")
                    .role(Role.OWNER)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedOwner);

            authService.createOwner(sampleRegisterDTO);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());

            User executedUserMapping = userCaptor.getValue();
            assertEquals(Role.OWNER, executedUserMapping.getRole());
            assertEquals("testuser", executedUserMapping.getUsername());
        }
    }
}
```

- [ ] **Step 6: Run the test**

Run: `cd microservices ; ./mvnw -pl user-service test -Dtest=AuthServiceTest`
Expected: PASS (all nested tests green).

- [ ] **Step 7: Commit**

```bash
git add microservices/user-service/src/main/java/com/awbd/cinema/clients microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService microservices/user-service/src/test/java/com/awbd/cinema/services/AuthServiceTest.java
git commit -m "Rewrite user-service AuthService to use notification Feign client with fallback"
```

---

### Task 10: `user-service` — loyalty-points API (`UserService` + `InternalUserController`)

Add the internal loyalty-points read/write the future booking-service will use to spend/award points. `GET` returns the current balance; `PATCH` sets the absolute new value (booking-service reads, computes, writes back). `UserController` and the existing `UserService` methods are ported verbatim; we extend the interface/impl and add a new controller.

**Files:**
- Create (extended): `microservices/user-service/src/main/java/com/awbd/cinema/services/UserService/UserService.java`
- Create (extended): `microservices/user-service/src/main/java/com/awbd/cinema/services/UserService/UserServiceImpl.java`
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/controllers/InternalUserController.java`
- Test: `microservices/user-service/src/test/java/com/awbd/cinema/services/UserServiceLoyaltyTest.java`

- [ ] **Step 1: Create the extended `UserService` interface**

Create `microservices/user-service/src/main/java/com/awbd/cinema/services/UserService/UserService.java` (monolith interface + 2 new methods):

```java
package com.awbd.cinema.services.UserService;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.security.CustomUserDetails;

import java.util.Map;

public interface UserService {
    ProfileDTO getProfile(Long id);
    Map<String,String> deleteAccount(CustomUserDetails userDetails);
    Map<String,String> deleteAccount(Long id);
    ProfileDTO updateProfile(Long id, UpdateProfileDTO dto);
    ProfileDTO promoteUser(PromoteDTO dto);
    LoyaltyPointsDTO getLoyaltyPoints(Long userId);
    LoyaltyPointsDTO updateLoyaltyPoints(Long userId, AdjustLoyaltyPointsDTO dto);
}
```

- [ ] **Step 2: Create the extended `UserServiceImpl`**

Create `microservices/user-service/src/main/java/com/awbd/cinema/services/UserService/UserServiceImpl.java` — the monolith impl plus the two new methods at the end:

```java
package com.awbd.cinema.services.UserService;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    public ProfileDTO getProfile(Long id){
        User u = userRepository.findById(id).orElseThrow(()->new NotFoundException("User doesn't exist."));
        return ProfileDTO.from(u);
    }

    @Override
    @Transactional
    public Map<String, String> deleteAccount(CustomUserDetails userDetails) {
        if(userDetails.getAuthorities().stream().anyMatch(r-> Objects.equals(r.getAuthority(), "ROLE_OWNER")))
            throw new BadRequestException("You cannot delete your account.");

        deleteUser(userDetails.getId());
        return Map.of("message", "Your account has been deleted successfully.");
    }

    @Override
    @Transactional
    public Map<String, String> deleteAccount(Long id) {
        String deletedUsername = deleteUser(id);
        return Map.of("message", deletedUsername + "'s account has been deleted successfully.");
    }

    @Transactional
    public ProfileDTO updateProfile(Long id, UpdateProfileDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User doesn't exist"));

        if (dto.firstName() != null) {
            user.setFirstName(dto.firstName());
        }

        if (dto.lastName() != null) {
            user.setLastName(dto.lastName());
        }

        if (dto.phoneNumber() != null) {
            user.setPhoneNumber(dto.phoneNumber());
        }

        if (dto.email() != null && !user.getEmail().equalsIgnoreCase(dto.email())) {
            userRepository.findByEmailIgnoreCase(dto.email()).ifPresent(existingUser -> {
                throw new AlreadyExistsException("Email address is already in use.");
            });
            user.setEmail(dto.email());
        }
        userRepository.save(user);
        return ProfileDTO.from(user);
    }

    @Override
    @Transactional
    public ProfileDTO promoteUser(PromoteDTO dto) {
        User user = userRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestException("User doesn't exist"));
        user.setRole(dto.role());
        userRepository.save(user);
        return ProfileDTO.from(user);
    }

    @Override
    @Transactional
    public LoyaltyPointsDTO getLoyaltyPoints(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User doesn't exist."));
        return new LoyaltyPointsDTO(u.getId(), u.getLoyaltyPoints());
    }

    @Override
    @Transactional
    public LoyaltyPointsDTO updateLoyaltyPoints(Long userId, AdjustLoyaltyPointsDTO dto) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User doesn't exist."));
        u.setLoyaltyPoints(dto.loyaltyPoints());
        userRepository.save(u);
        return new LoyaltyPointsDTO(u.getId(), u.getLoyaltyPoints());
    }

    private String deleteUser(Long id){
        User u = userRepository.findById(id).orElseThrow(()->new NotFoundException("User doesn't exist."));
        if(u.getDeletedAt() != null)
            throw new BadRequestException("User doesn't exist.");
        if(u.getRole() == Role.OWNER)
            throw new BadRequestException("You cannot delete this account.");

        UUID uuid = UUID.randomUUID();
        u.setUsername(u.getUsername() + "-deleted" + uuid);
        u.setEmail("deleted-" + uuid + "@example.com");
        u.setPhoneNumber("+0777777777");
        u.setFirstName("Deleted");
        u.setLastName("User");
        u.setDeletedAt(LocalDateTime.now());
        userRepository.save(u);
        return u.getUsername();
    }
}
```

- [ ] **Step 3: Create `InternalUserController`**

Create `microservices/user-service/src/main/java/com/awbd/cinema/controllers/InternalUserController.java`:

```java
package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.services.UserService.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}/loyalty-points")
    public ResponseEntity<LoyaltyPointsDTO> getLoyaltyPoints(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getLoyaltyPoints(id));
    }

    @PatchMapping("/{id}/loyalty-points")
    public ResponseEntity<LoyaltyPointsDTO> updateLoyaltyPoints(
            @PathVariable Long id, @Valid @RequestBody AdjustLoyaltyPointsDTO dto) {
        return ResponseEntity.ok(userService.updateLoyaltyPoints(id, dto));
    }
}
```

- [ ] **Step 4: Write the loyalty-points service test**

Create `microservices/user-service/src/test/java/com/awbd/cinema/services/UserServiceLoyaltyTest.java`:

```java
package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.UserService.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceLoyaltyTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserServiceImpl userService;

    @Test
    void getLoyaltyPoints_ReturnsCurrentBalance() {
        User user = User.builder().id(5L).username("bob").loyaltyPoints(120).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        LoyaltyPointsDTO result = userService.getLoyaltyPoints(5L);

        assertEquals(5L, result.userId());
        assertEquals(120, result.loyaltyPoints());
    }

    @Test
    void getLoyaltyPoints_ThrowsNotFound_WhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getLoyaltyPoints(99L));
    }

    @Test
    void updateLoyaltyPoints_SetsAbsoluteValueAndPersists() {
        User user = User.builder().id(5L).username("bob").loyaltyPoints(120).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        LoyaltyPointsDTO result = userService.updateLoyaltyPoints(5L, new AdjustLoyaltyPointsDTO(45));

        assertEquals(5L, result.userId());
        assertEquals(45, result.loyaltyPoints());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(45, captor.getValue().getLoyaltyPoints());
    }

    @Test
    void updateLoyaltyPoints_ThrowsNotFound_WhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.updateLoyaltyPoints(99L, new AdjustLoyaltyPointsDTO(10)));
        verify(userRepository, never()).save(any(User.class));
    }
}
```

- [ ] **Step 5: Run the test**

Run: `cd microservices ; ./mvnw -pl user-service test -Dtest=UserServiceLoyaltyTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add microservices/user-service/src/main/java/com/awbd/cinema/services/UserService microservices/user-service/src/main/java/com/awbd/cinema/controllers/InternalUserController.java microservices/user-service/src/test/java/com/awbd/cinema/services/UserServiceLoyaltyTest.java
git commit -m "Add internal loyalty-points API to user-service"
```

---

### Task 11: `user-service` — remaining test ports/adaptations + full reactor build

Port the rest of the user-service test suite. Three need edits because of the `CustomUserDetails` constructor change and the role-hierarchy bean move; the rest are verbatim copies. Then build the whole reactor green.

**Files:**
- Copy (verbatim): `CustomUserDetailsServiceTest`, `LoginAttemptServiceTest`, `AuthControllerTest`, `UserControllerTest`, `UserServiceTest`, `AuthIntegrationTest`
- Create (adapted): `BaseControllerTest`, `CustomAuthenticationProviderTest`

- [ ] **Step 1: Port the verbatim test files**

From the repo root:

```bash
New-Item -ItemType Directory -Force -Path microservices/user-service/src/test/java/com/awbd/cinema/security, microservices/user-service/src/test/java/com/awbd/cinema/services, microservices/user-service/src/test/java/com/awbd/cinema/controllers

Copy-Item cinema/src/test/java/com/awbd/cinema/security/CustomUserDetailsServiceTest.java microservices/user-service/src/test/java/com/awbd/cinema/security/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/LoginAttemptServiceTest.java microservices/user-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/services/UserServiceTest.java microservices/user-service/src/test/java/com/awbd/cinema/services/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/AuthControllerTest.java microservices/user-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/controllers/UserControllerTest.java microservices/user-service/src/test/java/com/awbd/cinema/controllers/
Copy-Item cinema/src/test/java/com/awbd/cinema/AuthIntegrationTest.java microservices/user-service/src/test/java/com/awbd/cinema/
```

> These are confirmed unchanged: `CustomUserDetailsServiceTest` only asserts on the `UserDetails` interface (no `CustomUserDetails` constructor call); `UserServiceTest`/`UserControllerTest` use `mock(CustomUserDetails.class)`; `AuthControllerTest` mocks `AuthService`; `AuthIntegrationTest` exercises `/auth/register` + `/auth/login` end-to-end against the test DB and is package-root `com.awbd.cinema`, which exists in user-service.

- [ ] **Step 2: Create the adapted `BaseControllerTest`**

Create `microservices/user-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java`. Two changes from the monolith: `loginAs()` uses the primitive `CustomUserDetails` constructor, and the `@Import` adds `RoleHierarchyConfig` (the role-hierarchy beans moved out of `SecurityConfig` into `common`, and `@EnableMethodSecurity` in `SecurityConfig` needs them).

```java
package com.awbd.cinema.controllers;

import com.awbd.cinema.config.RoleHierarchyConfig;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.security.JwtAuthenticationFilter;
import com.awbd.cinema.security.SecurityConfig;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import com.awbd.cinema.utils.SecurityCorsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest
@Import({SecurityConfig.class, RoleHierarchyConfig.class})
public abstract class BaseControllerTest {
    @MockitoBean
    protected UserDetailsService userDetailsService;

    @MockitoBean
    protected LoginAttemptService loginAttemptService;

    @MockitoBean
    protected JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    protected SecurityCorsProperties securityCorsProperties;

    @MockitoBean
    protected JwtUtil jwtUtil;

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

> The monolith `loginAs` built a `User` then wrapped it; the new version constructs `CustomUserDetails` directly (the `User` import is dropped). `emailVerifiedAt` is `null` here — harmless, since these MVC tests never hit `CustomAuthenticationProvider`.

- [ ] **Step 3: Create the adapted `CustomAuthenticationProviderTest`**

Port the monolith file, then change the two `new CustomUserDetails(rawUser)` calls to the primitive constructor. Copy it first:

```bash
Copy-Item cinema/src/test/java/com/awbd/cinema/security/CustomAuthenticationProviderTest.java microservices/user-service/src/test/java/com/awbd/cinema/security/
```

Then in `microservices/user-service/src/test/java/com/awbd/cinema/security/CustomAuthenticationProviderTest.java`, replace **both** occurrences of:

```java
        CustomUserDetails customUserDetails = new CustomUserDetails(rawUser);
```

with:

```java
        CustomUserDetails customUserDetails = new CustomUserDetails(
                rawUser.getId(), rawUser.getUsername(), rawUser.getPassword(),
                rawUser.getRole(), rawUser.getEmailVerifiedAt());
```

(The first occurrence is in `authenticate_ShouldThrowDisabledException_WhenCustomUserDetailsHasNullEmailVerifiedAt` where `rawUser` has `emailVerifiedAt(null)`; the second is in `authenticate_ShouldFullySucceed_WhenCustomUserDetailsHasVerifiedEmail` where it has `emailVerifiedAt(LocalDateTime.now())`. Both still build a `User` via `User.builder()` and then derive the `CustomUserDetails`, preserving the exact test semantics.)

- [ ] **Step 4: Run the full user-service test suite**

Run: `cd microservices ; ./mvnw -pl user-service test`
Expected: all tests PASS (AuthServiceTest, UserServiceTest, UserServiceLoyaltyTest, CustomAuthenticationProviderTest, CustomUserDetailsServiceTest, LoginAttemptServiceTest, AuthControllerTest, UserControllerTest, AuthIntegrationTest).

> If `AuthIntegrationTest` fails to find a database, ensure the existing Postgres (`docker compose up postgres`) is running, or that the H2 runtime dependency resolves the `jdbc:postgresql` URL is reachable — for a pure unit-feedback loop you may run `-Dtest='!AuthIntegrationTest'` to skip it, but the full `verify` in Step 5 expects the DB up, consistent with how the monolith runs this test.

- [ ] **Step 5: Build the entire reactor**

Run: `cd microservices ; ./mvnw clean verify`
Expected: `BUILD SUCCESS` for both `common` and `user-service`.

- [ ] **Step 6: Manual smoke test (optional but recommended)**

With the existing Postgres running and `.env` present at the repo root (or `microservices/user-service/`), start the service:

Run: `cd microservices ; ./mvnw -pl user-service spring-boot:run`

Then:
- `POST http://localhost:8081/api/v1/auth/register` with a JSON body `{"username":"smoke","password":"Password123!","confirmPassword":"Password123!","email":"smoke@example.com","firstName":"Smoke","lastName":"Test","phoneNumber":"+1234567890"}` → expect `201` and a log line `booking-service unavailable; skipping notification ...` (the Feign fallback firing, proving resilience).
- `POST .../auth/login` with `{"username":"smoke","password":"Password123!"}` → expect `200` + `jwt`/`refresh` cookies.
- `GET .../internal/users/1/loyalty-points` → expect `200` with `{"userId":1,"loyaltyPoints":0}`.

- [ ] **Step 7: Commit**

```bash
git add microservices/user-service/src/test
git commit -m "Port and adapt remaining user-service tests; full reactor builds green"
```

---

## Phase 1 Done — What Exists Now

- `microservices/` reactor with `common` (shared jar) + `user-service` (runnable Spring Boot app on `:8081`).
- Claims-based stateless JWT end-to-end: login mints `userId`+`role`; the `common` filter authenticates every request with no DB lookup.
- Internal loyalty-points API + a resilient (circuit-broken, fallback-logged) notification Feign client ready for booking-service.
- Full ported test suite green.

**Deferred to later phases (not gaps):** `catalog-service` (Phase 2), `booking-service` + remaining Feign integrations + entity denormalization (Phase 3), API gateway + `docker-compose.microservices.yml` + Config Server + Eureka/discovery + Actuator dashboards + the requirement areas that depend on multiple services running together (Phase 4).
