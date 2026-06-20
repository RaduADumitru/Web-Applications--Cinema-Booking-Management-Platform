# Distributed Security — Service-to-Service JWT Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Authenticate the microservices' internal (`/internal/**`) endpoints with a short-lived, service-minted JWT so they are no longer open (`permitAll`), closing the service-to-service security gap (coursework requirement #6).

**Architecture:** Each calling service mints a short-lived JWT (`typ=SERVICE`, `sub=<service-name>`, ~60s, signed with the existing shared `jwt.secret.key`) and sends it as `Authorization: Bearer …` on every Feign call (a new `ServiceTokenInterceptor` in `common`, replacing the cookie-forwarding `FeignAuthInterceptor`). Every service gains a dedicated `@Order(1)` `SecurityFilterChain` matched to `/internal/**` that validates the bearer token via a `ServiceTokenAuthenticationFilter` and requires `ROLE_SERVICE`; the existing chain becomes `@Order(2)` and drops its `/internal/**` `permitAll`. The `typ` claim keeps service tokens and user `ACCESS` tokens non-interchangeable, so the same secret is safe.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Cloud 2025.1.2, Spring Security (servlet), jjwt, OpenFeign, JUnit 5 + Mockito + AssertJ, MockMvc, H2 (test).

**Design reference:** `docs/superpowers/specs/2026-06-20-distributed-security-design.md`

**Conventions in this repo (already verified):**
- Run all Maven commands from `microservices/` (the reactor root). `mvn` is Maven 3.9.x + Java 21 on PATH.
- Test bypass of the config server is already wired per service via `src/test/resources/application.properties` (`spring.cloud.config.enabled=false`); the test profile (`application-test.yml`) supplies `jwt.secret.key`. New `@SpringBootTest` tests inherit both automatically.
- `common` shares the `com.awbd.cinema` base package with every service, so a `@Component` in `common` is auto-registered everywhere (this is how the global Feign interceptor works).
- **Project-memory lesson:** shared servlet `Filter`s placed as `@Component` in `common` break service `@WebMvcTest` slices. The new `ServiceTokenAuthenticationFilter` is therefore a plain class instantiated inside each `SecurityConfig` (like the existing `CsrfCookieFilter`), NOT a `@Component`.

---

### Task 1: `JwtUtil` — service-token generation & validation (`common`)

**Files:**
- Modify: `microservices/common/src/main/java/com/awbd/cinema/utils/JwtUtil.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/utils/JwtUtilTest.java`

- [ ] **Step 1: Add the failing tests**

In `JwtUtilTest.java`, first update the existing `setUp()` to also set the new TTL field (otherwise the field defaults to `0` and minted tokens expire instantly):

```java
    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", testSecretKey);
        ReflectionTestUtils.setField(jwtUtil, "serviceTokenTtlSeconds", 60L);
    }
```

Add these imports at the top of the test file:

```java
import io.jsonwebtoken.JwtException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

Then add these test methods:

```java
    @Test
    void generateServiceToken_ShouldGenerateValidServiceToken() {
        String token = jwtUtil.generateServiceToken("booking-service");

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("booking-service");
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("SERVICE");
    }

    @Test
    void validateServiceToken_ShouldReturnServiceName_WhenTokenIsValid() {
        String token = jwtUtil.generateServiceToken("catalog-service");

        assertThat(jwtUtil.validateServiceToken(token)).isEqualTo("catalog-service");
    }

    @Test
    void validateServiceToken_ShouldThrow_WhenTokenIsAnAccessToken() {
        String accessToken = jwtUtil.generateToken("user", 1L, Role.USER);

        assertThatThrownBy(() -> jwtUtil.validateServiceToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateServiceToken_ShouldThrow_WhenSignatureIsTampered() {
        String token = jwtUtil.generateServiceToken("booking-service");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtUtil.validateServiceToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateServiceToken_ShouldThrow_WhenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtUtil, "serviceTokenTtlSeconds", -10L);
        String token = jwtUtil.generateServiceToken("booking-service");

        assertThatThrownBy(() -> jwtUtil.validateServiceToken(token))
                .isInstanceOf(JwtException.class);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl common test -Dtest=JwtUtilTest`
Expected: compilation failure / FAIL — `generateServiceToken` and `validateServiceToken` do not exist yet.

- [ ] **Step 3: Implement the new methods**

In `JwtUtil.java`, add the import for `JwtException`:

```java
import io.jsonwebtoken.JwtException;
```

Add a TTL field below the existing `secretKey` field:

```java
    @Value("${service.token.ttl-seconds:60}")
    private long serviceTokenTtlSeconds;
```

Add these two methods (e.g. after `generateRefreshToken`):

```java
    public String generateServiceToken(String serviceName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(serviceName)
                .claim("typ", "SERVICE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(serviceTokenTtlSeconds)))
                .signWith(getSigningKey())
                .compact();
    }

    public String validateServiceToken(String token) {
        // extractClaim parses & verifies the signature and throws on expiry.
        String type = extractClaim(token, claims -> claims.get("typ", String.class));
        if (!"SERVICE".equals(type)) {
            throw new JwtException("Token is not a service token");
        }
        return extractClaim(token, Claims::getSubject);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -o -pl common test -Dtest=JwtUtilTest`
Expected: PASS (all existing + 5 new tests green).

- [ ] **Step 5: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/utils/JwtUtil.java microservices/common/src/test/java/com/awbd/cinema/utils/JwtUtilTest.java
git commit -m "Add service-token generation and validation to JwtUtil"
```

---

### Task 2: `ServiceTokenAuthenticationFilter` (`common`)

A plain `OncePerRequestFilter` (NOT `@Component`) that reads `Authorization: Bearer …`, validates it as a service token, and on success sets an authentication carrying a single `ROLE_SERVICE` authority.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/security/ServiceTokenAuthenticationFilter.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/security/ServiceTokenAuthenticationFilterTest.java`

- [ ] **Step 1: Write the failing test**

Create `ServiceTokenAuthenticationFilterTest.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTokenAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private ServiceTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ShouldAuthenticateAsService_WhenBearerTokenIsValid()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer service_token");
        when(jwtUtil.validateServiceToken("service_token")).thenReturn("booking-service");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("booking-service");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldNotAuthenticate_WhenNoAuthorizationHeader()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldNotAuthenticate_WhenTokenIsInvalid()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad_token");
        when(jwtUtil.validateServiceToken("bad_token")).thenThrow(new JwtException("bad"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl common test -Dtest=ServiceTokenAuthenticationFilterTest`
Expected: compilation failure — `ServiceTokenAuthenticationFilter` does not exist yet.

- [ ] **Step 3: Implement the filter**

Create `ServiceTokenAuthenticationFilter.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String serviceName = jwtUtil.validateServiceToken(token);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                serviceName, null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("Internal request authenticated as service '{}' -> {} {}",
                        serviceName, request.getMethod(), request.getRequestURI());
            } catch (JwtException e) {
                log.warn("Rejecting internal request with invalid service token: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl common test -Dtest=ServiceTokenAuthenticationFilterTest`
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/security/ServiceTokenAuthenticationFilter.java microservices/common/src/test/java/com/awbd/cinema/security/ServiceTokenAuthenticationFilterTest.java
git commit -m "Add ServiceTokenAuthenticationFilter for /internal endpoint auth"
```

---

### Task 3: `ServiceTokenInterceptor` + remove `FeignAuthInterceptor` (`common`)

Replace the user-cookie-forwarding Feign interceptor with one that mints and attaches a service token on every outgoing Feign call.

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/config/ServiceTokenInterceptor.java`
- Delete: `microservices/common/src/main/java/com/awbd/cinema/config/FeignAuthInterceptor.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/config/ServiceTokenInterceptorTest.java`

- [ ] **Step 1: Write the failing test**

Create `ServiceTokenInterceptorTest.java`:

```java
package com.awbd.cinema.config;

import com.awbd.cinema.utils.JwtUtil;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenInterceptorTest {

    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private ServiceTokenInterceptor interceptor;

    @Test
    void apply_ShouldAddBearerAuthorizationHeaderWithMintedServiceToken() {
        ReflectionTestUtils.setField(interceptor, "applicationName", "booking-service");
        when(jwtUtil.generateServiceToken("booking-service")).thenReturn("minted_token");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer minted_token");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl common test -Dtest=ServiceTokenInterceptorTest`
Expected: compilation failure — `ServiceTokenInterceptor` does not exist yet.

- [ ] **Step 3: Implement the interceptor and delete the old one**

Create `ServiceTokenInterceptor.java`:

```java
package com.awbd.cinema.config;

import com.awbd.cinema.utils.JwtUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTokenInterceptor implements RequestInterceptor {

    private final JwtUtil jwtUtil;

    @Value("${spring.application.name}")
    private String applicationName;

    @Override
    public void apply(RequestTemplate template) {
        String token = jwtUtil.generateServiceToken(applicationName);
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        log.info("Attached service token as '{}' for outgoing {} {}",
                applicationName, template.method(), template.path());
    }
}
```

Delete `FeignAuthInterceptor.java`:

```bash
git rm microservices/common/src/main/java/com/awbd/cinema/config/FeignAuthInterceptor.java
```

> If a `FeignAuthInterceptorTest` exists, `git rm` it too. (At the time of writing there is none — the grep for usages found only the class itself.)

- [ ] **Step 4: Run the test to verify it passes, then build `common`**

Run: `mvn -o -pl common test -Dtest=ServiceTokenInterceptorTest`
Expected: PASS.

Run: `mvn -o -pl common test`
Expected: PASS — the whole `common` module is green and no test references the deleted `FeignAuthInterceptor`.

- [ ] **Step 5: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/config/ServiceTokenInterceptor.java microservices/common/src/test/java/com/awbd/cinema/config/ServiceTokenInterceptorTest.java
git commit -m "Replace FeignAuthInterceptor with service-token-minting ServiceTokenInterceptor"
```

---

### Task 4: catalog-service — `/internal/**` security chain + endpoint auth test

**Files:**
- Modify: `microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`
- Modify: `microservices/catalog-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java` (add `@MockitoBean protected JwtUtil jwtUtil;` — see note below)
- Test: `microservices/catalog-service/src/test/java/com/awbd/cinema/security/InternalCatalogSecurityTest.java`

> **`@WebMvcTest` slice fix (required):** adding `JwtUtil` as a `SecurityConfig` constructor dependency makes every `@WebMvcTest` that `@Import`s `SecurityConfig` fail to load its context unless `JwtUtil` is supplied to the slice. The existing `BaseControllerTest` already mocks `SecurityConfig`'s other `common` deps (`JwtAuthenticationFilter`, `SecurityCorsProperties`) via `@MockitoBean`; add `@MockitoBean protected JwtUtil jwtUtil;` alongside them (import `com.awbd.cinema.utils.JwtUtil`). This is the same project-memory lesson about `common` web beans in `@WebMvcTest` slices. (user-service's `BaseControllerTest` already mocks `JwtUtil`, so Task 5 needs no such change; booking-service does, so Task 6 repeats this step.)

- [ ] **Step 1: Write the failing test**

Create `InternalCatalogSecurityTest.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.services.TicketSetupService.TicketSetupService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalCatalogSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private TicketSetupService ticketSetupService;

    @Test
    void ticketSetup_ShouldReturn401_WhenNoServiceToken() throws Exception {
        mockMvc.perform(get("/internal/ticket-setup")
                        .param("seatId", "1").param("roomId", "1").param("sessionId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketSetup_ShouldReturn200_WhenServiceTokenIsValid() throws Exception {
        String token = jwtUtil.generateServiceToken("booking-service");

        mockMvc.perform(get("/internal/ticket-setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("seatId", "1").param("roomId", "1").param("sessionId", "1"))
                .andExpect(status().isOk());
    }
}
```

> The `@MockitoBean` returns `null` from `getTicketSetup(...)`; the controller wraps it in `ResponseEntity.ok(null)` → 200, so no DTO needs to be constructed. The 401 case is enforced by security before the controller runs. (Import `org.springframework.test.context.bean.override.mockito.MockitoBean` — the package this repo's existing tests use.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl catalog-service test -Dtest=InternalCatalogSecurityTest`
Expected: FAIL — `ticketSetup_ShouldReturn401_WhenNoServiceToken` gets 200 (the endpoint is still `permitAll`).

- [ ] **Step 3: Add the `/internal/**` chain and demote the main chain**

In `catalog-service/.../security/SecurityConfig.java`:

(a) Add imports:

```java
import com.awbd.cinema.utils.JwtUtil;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
```

(`UsernamePasswordAuthenticationFilter` is likely already imported — if so, skip the duplicate.)

(b) Add a `JwtUtil` field to the injected dependencies (alongside `jwtAuthenticationFilter`):

```java
    private final JwtUtil jwtUtil;
```

(c) Annotate the existing `filterChain` bean with `@Order(2)`:

```java
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
```

(d) In that same `filterChain`, REMOVE the now-dead `/internal/**` line:

```java
                        .requestMatchers("/internal/**").permitAll()
```

and REMOVE `"/internal/**"` from the CSRF ignore list so it reads:

```java
                    .ignoringRequestMatchers("/actuator/**")
```

(e) Add the new internal chain bean (place it directly above `filterChain`):

```java
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new UnauthenticatedException("Service authentication required."))))
                .authorizeHttpRequests(authz -> authz.anyRequest().hasRole("SERVICE"))
                .addFilterBefore(new ServiceTokenAuthenticationFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
```

> `ServiceTokenAuthenticationFilter` and `UnauthenticatedException` are already importable here (`ServiceTokenAuthenticationFilter` shares the `com.awbd.cinema.security` package; `UnauthenticatedException` is already imported by the existing config).

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl catalog-service test -Dtest=InternalCatalogSecurityTest`
Expected: PASS (401 without token, 200 with a valid service token).

- [ ] **Step 5: Run the full catalog-service test suite (no regressions)**

Run: `mvn -o -pl catalog-service test`
Expected: PASS — all prior catalog tests still green.

- [ ] **Step 6: Commit**

```bash
git add microservices/catalog-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java microservices/catalog-service/src/test/java/com/awbd/cinema/security/InternalCatalogSecurityTest.java
git commit -m "Require service token on catalog-service /internal endpoints"
```

---

### Task 5: user-service — `/internal/**` security chain + endpoint auth test

Same two-chain change. user-service's internal endpoint is `GET /internal/users/{id}/loyalty-points` (no request body — the positive test needs only the token).

**Files:**
- Modify: `microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`
- Test: `microservices/user-service/src/test/java/com/awbd/cinema/security/InternalUserSecurityTest.java`

- [ ] **Step 1: Write the failing test**

Create `InternalUserSecurityTest.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.services.UserService.UserService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalUserSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private UserService userService;

    @Test
    void getLoyaltyPoints_ShouldReturn401_WhenNoServiceToken() throws Exception {
        mockMvc.perform(get("/internal/users/1/loyalty-points"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLoyaltyPoints_ShouldReturn200_WhenServiceTokenIsValid() throws Exception {
        String token = jwtUtil.generateServiceToken("booking-service");

        mockMvc.perform(get("/internal/users/1/loyalty-points")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }
}
```

> `UserService.getLoyaltyPoints(1L)` returns `null` from the mock → `ResponseEntity.ok(null)` → 200. (Import `org.springframework.test.context.bean.override.mockito.MockitoBean`, as in Task 4.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl user-service test -Dtest=InternalUserSecurityTest`
Expected: FAIL — the 401 test gets 200 (endpoint still `permitAll`).

- [ ] **Step 3: Add the `/internal/**` chain and demote the main chain**

In `user-service/.../security/SecurityConfig.java`:

(a) Add imports:

```java
import com.awbd.cinema.utils.JwtUtil;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
```

(`UsernamePasswordAuthenticationFilter` is already imported in this file.)

(b) Add a `JwtUtil` field to the injected dependencies:

```java
    private final JwtUtil jwtUtil;
```

(c) Annotate the existing `filterChain` bean with `@Order(2)`:

```java
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
```

(d) In `filterChain`, REMOVE the `/internal/**` permit line:

```java
                        .requestMatchers("/internal/**").permitAll()
```

and REMOVE `"/internal/**"` from the CSRF ignore list so it reads:

```java
                    .ignoringRequestMatchers("/auth/**", "/actuator/**")
```

(e) Add the internal chain bean directly above `filterChain` (identical body to Task 4 step 3e):

```java
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new UnauthenticatedException("Service authentication required."))))
                .authorizeHttpRequests(authz -> authz.anyRequest().hasRole("SERVICE"))
                .addFilterBefore(new ServiceTokenAuthenticationFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl user-service test -Dtest=InternalUserSecurityTest`
Expected: PASS.

- [ ] **Step 5: Run the full user-service test suite (no regressions)**

Run: `mvn -o -pl user-service test`
Expected: PASS — including `AuthIntegrationTest` (registration still succeeds; the user→booking notification call uses the fallback in tests and is unaffected by the new interceptor).

- [ ] **Step 6: Commit**

```bash
git add microservices/user-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java microservices/user-service/src/test/java/com/awbd/cinema/security/InternalUserSecurityTest.java
git commit -m "Require service token on user-service /internal endpoints"
```

---

### Task 6: booking-service — `/internal/**` security chain + endpoint auth test

booking-service's internal endpoint is `POST /internal/notifications` (validated `CreateNotificationDTO` body).

**Files:**
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java`
- Modify: `microservices/booking-service/src/test/java/com/awbd/cinema/controllers/BaseControllerTest.java` (add `@MockitoBean protected JwtUtil jwtUtil;` — same `@WebMvcTest` slice fix as Task 4)
- Test: `microservices/booking-service/src/test/java/com/awbd/cinema/security/InternalNotificationSecurityTest.java`

- [ ] **Step 1: Write the failing test**

Create `InternalNotificationSecurityTest.java`:

```java
package com.awbd.cinema.security;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.services.NotificationService.NotificationService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalNotificationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private NotificationService notificationService;

    @Test
    void createNotification_ShouldReturn401_WhenNoServiceToken() throws Exception {
        CreateNotificationDTO dto =
                new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "Welcome!", 1L);

        mockMvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createNotification_ShouldReturn201_WhenServiceTokenIsValid() throws Exception {
        CreateNotificationDTO dto =
                new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "Welcome!", 1L);
        String token = jwtUtil.generateServiceToken("user-service");

        mockMvc.perform(post("/internal/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }
}
```

> No `csrf()` post-processor is needed: the internal chain disables CSRF. `NotificationService.createNotification(...)` returns `null` from the mock → `ResponseEntity.status(CREATED).body(null)` → 201. If `NotificationType.EMAIL_VERIFICATION` is not the exact constant name, substitute any valid `NotificationType` constant (it only needs to satisfy `@NotNull`). (Import `org.springframework.test.context.bean.override.mockito.MockitoBean`, as in Task 4.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl booking-service test -Dtest=InternalNotificationSecurityTest`
Expected: FAIL — the 401 test gets 201 (endpoint still `permitAll`).

- [ ] **Step 3: Add the `/internal/**` chain and demote the main chain**

In `booking-service/.../security/SecurityConfig.java` apply the SAME edits as Task 4 step 3:

(a) Add imports:

```java
import com.awbd.cinema.utils.JwtUtil;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
```

(`UsernamePasswordAuthenticationFilter` may already be imported — skip if so.)

(b) Add the field:

```java
    private final JwtUtil jwtUtil;
```

(c) Annotate the existing `filterChain` with `@Order(2)`.

(d) REMOVE from `filterChain`:

```java
                        .requestMatchers("/internal/**").permitAll()
```

and drop `"/internal/**"` from the CSRF ignore list so it reads:

```java
                    .ignoringRequestMatchers("/actuator/**")
```

(e) Add the internal chain bean directly above `filterChain` (identical body to Task 4 step 3e):

```java
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                handlerExceptionResolver.resolveException(request, response, null,
                                        new UnauthenticatedException("Service authentication required."))))
                .authorizeHttpRequests(authz -> authz.anyRequest().hasRole("SERVICE"))
                .addFilterBefore(new ServiceTokenAuthenticationFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o -pl booking-service test -Dtest=InternalNotificationSecurityTest`
Expected: PASS.

- [ ] **Step 5: Run the full booking-service test suite (no regressions)**

Run: `mvn -o -pl booking-service test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add microservices/booking-service/src/main/java/com/awbd/cinema/security/SecurityConfig.java microservices/booking-service/src/test/java/com/awbd/cinema/security/InternalNotificationSecurityTest.java
git commit -m "Require service token on booking-service /internal endpoints"
```

---

### Task 7: Config visibility, docs, and full-reactor verification

Make the TTL explicit in shared config, document the change, and run the whole reactor green before pushing (per the project-memory lesson about `common` web beans + `@WebMvcTest` slices).

**Files:**
- Modify: `microservices/config-server/config-repo/application.yml`
- Modify: `README.md`

- [ ] **Step 1: Surface the service-token TTL in shared config**

In `microservices/config-server/config-repo/application.yml`, add a top-level block (e.g. just after the `jwt:` block) so the runtime value is visible/tunable. The `JwtUtil` `@Value` default of `60` already applies if this is absent (and in tests), so this is documentation/operability, not a hard requirement:

```yaml
service:
  token:
    # TTL (seconds) for service-to-service JWTs minted per outgoing Feign call.
    ttl-seconds: ${SERVICE_TOKEN_TTL_SECONDS:60}
```

- [ ] **Step 2: Document the change in the README**

In `README.md`, under the microservices section, add a short subsection (place it near the existing gateway / `/internal/**` notes):

```markdown
## Service-to-service security

Internal endpoints (`/internal/**`) are authenticated with a short-lived
service JWT, not just network isolation. On every outgoing Feign call the
calling service mints a ~60s token (`typ=SERVICE`, `sub=<service-name>`,
signed with the shared `jwt.secret.key`) and sends it as
`Authorization: Bearer …`. Each service enforces a dedicated `/internal/**`
security chain that validates the token and requires `ROLE_SERVICE`. This is
independent of the user's `jwt` cookie (which still authenticates public
endpoints) — the `typ` claim keeps the two token kinds non-interchangeable.
The TTL is tunable via `SERVICE_TOKEN_TTL_SECONDS` (default 60).

### Seeing it work (logs)

Every internal call leaves a matched pair of `INFO` lines across two
services' logs — one where the caller mints/attaches the token, one where
the callee verifies it. Registration is the easiest trigger (user-service →
booking-service):

```bash
# Trigger a user->booking internal call
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!","confirmPassword":"Password123!","email":"demo@example.com","firstName":"Demo","lastName":"User","phoneNumber":"+1234567890"}' >/dev/null

# Caller side (user-service minted + attached the token):
docker compose -f docker-compose.microservices.yml logs user-service | grep "Attached service token"
# Callee side (booking-service verified it as ROLE_SERVICE):
docker compose -f docker-compose.microservices.yml logs booking-service | grep "authenticated as service"
```

Negative proof — calling an internal endpoint directly (bypassing the
gateway, via a service's debug host port) is rejected:

```bash
# No token -> 401
curl -i "http://localhost:8082/api/v1/internal/ticket-setup?seatId=1&roomId=1&sessionId=1"
# Forged token -> 401, and catalog-service logs "Rejecting internal request with invalid service token"
curl -i -H "Authorization: Bearer garbage" \
  "http://localhost:8082/api/v1/internal/ticket-setup?seatId=1&roomId=1&sessionId=1"
```
```

- [ ] **Step 3: Build and verify the entire reactor**

Run: `mvn -o clean verify` (from `microservices/`)
Expected: `BUILD SUCCESS` for every module. `common` gains the 3 new test classes' methods; each of `user-service`, `catalog-service`, `booking-service` gains one internal-security test class (2 tests each), all green; `gateway` and the other modules are unchanged. No module references the deleted `FeignAuthInterceptor`.

> If `mvn -o` fails to resolve a dependency (offline cache miss), drop `-o`.

- [ ] **Step 4: Commit**

```bash
git add microservices/config-server/config-repo/application.yml README.md
git commit -m "Document service-to-service JWT security and surface its TTL in shared config"
```

---

## Plan Complete — What Exists Now

- `JwtUtil` mints and validates `typ=SERVICE` tokens (shared secret, ~60s TTL).
- `ServiceTokenInterceptor` (replacing `FeignAuthInterceptor`) attaches a freshly-minted service token as `Authorization: Bearer …` on every Feign call.
- `ServiceTokenAuthenticationFilter` validates that token and grants `ROLE_SERVICE`.
- Each of the three services has a dedicated `@Order(1)` `/internal/**` security chain requiring `ROLE_SERVICE`; the main chain (`@Order(2)`) no longer treats `/internal/**` as `permitAll`. `/actuator/**` stays open for healthchecks.
- The registration flow (user→booking `/internal/notifications`, which fires before any user JWT exists) now authenticates with a real service credential instead of relying on `permitAll`.
- **Demo:** each internal call emits a matched pair of `INFO` log lines (caller "Attached service token…", callee "…authenticated as service…"), and a direct `curl` to an internal endpoint without/with a forged token returns 401 (and logs the rejection) — documented in the README.
- HTTPS remains deferred (design §9), along with per-caller allowlisting and a separate service-token key, as documented future hardening.
