# Circuit Breaker Resilience Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Resilience4j retry layer and consolidate retry + circuit breaker + fallback into one explicit wrapper per Feign client, with retry event logging so error behaviour is demoable by stopping a downstream service.

**Architecture:** Replace OpenFeign's implicit circuit-breaker integration with explicit `@Component` "gateway" wrappers. Each gateway method carries `@CircuitBreaker(name=…)` + `@Retry(name=…, fallbackMethod=…)`; the raw `@FeignClient` interfaces become thin HTTP callers (no `fallbackFactory`). All resilience tuning is centralized in config-server YAML.

**Tech Stack:** Spring Boot 4.0.7, Spring Cloud 2025.1.2, Resilience4j 2.3.0 (already on the classpath via `spring-cloud-starter-circuitbreaker-resilience4j:5.0.2`), OpenFeign, JUnit 5, Mockito, AssertJ.

---

## Background the engineer must know

- **Aspect order (fixed by Resilience4j):** `Retry( CircuitBreaker( method ) )`. Retry is the **outermost** aspect. Therefore `fallbackMethod` goes on **`@Retry`** so the fallback runs once, *after* all retry attempts are exhausted (not on every circuit-breaker attempt). Putting the fallback on `@CircuitBreaker` instead would make a void/skip fallback return success on the first attempt and silently suppress retries — do not do that.
- **Retry only transient faults:** the retry instance whitelists `feign.RetryableException` (connection refused/timeouts — Feign wraps `IOException` in this) and `feign.FeignException$FeignServerException` (5xx; Spring Cloud LoadBalancer returns a synthetic **503** when no instance is available, which also lands here). Everything else (4xx business errors, `CallNotPermittedException` from an open circuit) is **not** retried, but still flows to the fallback, which uses `FeignClientErrorTranslator` to surface real 4xx and degrade only on genuine outages.
- **`FeignClientErrorTranslator`** (in `common`, package `com.awbd.cinema.config`) is unchanged and reused by every gateway fallback. Its `clientErrorOrNull(Throwable)` returns the matching domain exception for a 4xx cause, else `null`.
- **Build:** this is a multi-module Maven reactor under `microservices/`. Use the system Maven (`mvn`, at `C:\Program Files\Maven\apache-maven-3.9.11`) — there is no Maven wrapper in `microservices/`. Run all commands from `microservices/`.
- **Commit discipline:** commit after each task. Branch is `enhance-circuit-breakers`.

## File Structure (created / modified)

**config-server**
- Modify: `microservices/config-server/config-repo/application.yml` — disable OpenFeign CB, add `resilience4j.*` instances.

**common**
- Modify: `microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerLoggingConfig.java` — add retry event logging.
- Delete: `microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerConfiguration.java` and `microservices/common/src/test/java/com/awbd/cinema/config/CircuitBreakerConfigurationTest.java` — dead code.

**booking-service**
- Create: `clients/CatalogServiceGateway.java`, `clients/UserServiceGateway.java` (+ unit tests).
- Create: `resilience/Resilience4jAnnotationSmokeTest.java` (test only).
- Modify: `clients/CatalogServiceClient.java`, `clients/UserServiceClient.java` — drop `fallbackFactory=`.
- Delete: `clients/CatalogServiceClientFallbackFactory.java`, `clients/UserServiceClientFallbackFactory.java` (+ their tests).
- Modify: `services/TicketService/TicketServiceImpl.java`, `services/OrderService/OrderServiceImpl.java` — inject gateways.
- Modify tests: `services/TicketServiceTest.java`, `services/OrderServiceTest.java`.
- Modify: `src/main/resources/application-test.yml` — disable OpenFeign CB.

**user-service**
- Create: `clients/NotificationGateway.java` (+ unit test).
- Modify: `clients/BookingServiceClient.java` — drop `fallbackFactory=`.
- Delete: `clients/BookingServiceClientFallbackFactory.java` (+ its test).
- Modify: `services/AuthService/AuthServiceImpl.java` — inject gateway.
- Modify test: `services/AuthServiceTest.java`.
- Modify: `src/main/resources/application-test.yml` — disable OpenFeign CB.

---

## Task 1: Centralize Resilience4j config & disable OpenFeign circuit breaker

**Files:**
- Modify: `microservices/config-server/config-repo/application.yml`
- Modify: `microservices/booking-service/src/main/resources/application-test.yml`
- Modify: `microservices/user-service/src/main/resources/application-test.yml`

- [ ] **Step 1: Disable OpenFeign CB and add Resilience4j instances in config-server**

In `microservices/config-server/config-repo/application.yml`, change the existing block:

```yaml
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
```

to:

```yaml
  cloud:
    openfeign:
      circuitbreaker:
        # Disabled: resilience is now owned explicitly by the @Retry + @CircuitBreaker
        # gateway wrappers, not OpenFeign's implicit integration.
        enabled: false
```

Then add this top-level block at the end of the file (same indentation level as `spring:`, `eureka:`, `logging:`):

```yaml
# Inter-service resilience. One retry + circuitbreaker instance per downstream service,
# named to match the @FeignClient name used in @Retry(name=...) / @CircuitBreaker(name=...).
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        # Retry ONLY transient faults. 4xx business errors and an open circuit
        # (CallNotPermittedException) are deliberately excluded.
        retry-exceptions:
          - feign.RetryableException
          - feign.FeignException$FeignServerException
          - java.io.IOException
    instances:
      catalog-service:
        base-config: default
      user-service:
        base-config: default
      booking-service:
        base-config: default
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
        # Benign 4xx mean the downstream is healthy and correctly rejecting a request,
        # so they must not trip the breaker (mirrors the old CircuitBreakerConfiguration).
        ignore-exceptions:
          - feign.FeignException$NotFound
          - feign.FeignException$BadRequest
          - feign.FeignException$Conflict
          - feign.FeignException$UnprocessableEntity
    instances:
      catalog-service:
        base-config: default
      user-service:
        base-config: default
      booking-service:
        base-config: default
```

- [ ] **Step 2: Disable OpenFeign CB in both test profiles**

In `microservices/booking-service/src/main/resources/application-test.yml` AND `microservices/user-service/src/main/resources/application-test.yml`, find:

```yaml
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
```

and change `enabled: true` to `enabled: false` in both files.

- [ ] **Step 3: Commit**

```bash
git add microservices/config-server/config-repo/application.yml \
        microservices/booking-service/src/main/resources/application-test.yml \
        microservices/user-service/src/main/resources/application-test.yml
git commit -m "config: add resilience4j retry+circuitbreaker instances, disable openfeign CB"
```

---

## Task 2: Add retry event logging to CircuitBreakerLoggingConfig (common)

**Files:**
- Modify: `microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerLoggingConfig.java`

This binds `RetryRegistry` events so retries show up in the console alongside the existing circuit-breaker logs — the core of the demo.

- [ ] **Step 1: Replace the file contents**

Replace the entire contents of `CircuitBreakerLoggingConfig.java` with:

```java
package com.awbd.cinema.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Logs Resilience4j retry and circuit-breaker activity (retry attempts, recorded failures, state
 * transitions and rejected calls) for every gateway across all services that depend on
 * {@code common}. This surfaces retry / fallback / circuit-breaker behaviour that would otherwise
 * be silent and is the basis for demonstrating error handling by stopping a downstream service.
 * Messages are logged at WARN, which is intentionally console-only (the file appender threshold is
 * ERROR).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerLoggingConfig {

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;
    private final ObjectProvider<RetryRegistry> retryRegistryProvider;

    @PostConstruct
    void registerEventLogging() {
        registerCircuitBreakerLogging();
        registerRetryLogging();
    }

    private void registerCircuitBreakerLogging() {
        CircuitBreakerRegistry registry = circuitBreakerRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("No CircuitBreakerRegistry present; circuit-breaker event logging disabled.");
            return;
        }
        // Circuit breakers are created lazily on first use, so bind both existing and future ones.
        registry.getAllCircuitBreakers().forEach(this::bindLogging);
        registry.getEventPublisher().onEntryAdded(event -> bindLogging(event.getAddedEntry()));
    }

    private void registerRetryLogging() {
        RetryRegistry registry = retryRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("No RetryRegistry present; retry event logging disabled.");
            return;
        }
        registry.getAllRetries().forEach(this::bindLogging);
        registry.getEventPublisher().onEntryAdded(event -> bindLogging(event.getAddedEntry()));
    }

    private void bindLogging(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();
        circuitBreaker.getEventPublisher()
                .onError(event -> log.warn("CircuitBreaker '{}' recorded a failed call: {}",
                        name, event.getThrowable().toString()))
                .onStateTransition(event -> log.warn("CircuitBreaker '{}' state transition {}",
                        name, event.getStateTransition()))
                .onCallNotPermitted(event -> log.warn("CircuitBreaker '{}' rejected a call (circuit is OPEN).",
                        name));
    }

    private void bindLogging(Retry retry) {
        String name = retry.getName();
        retry.getEventPublisher()
                .onRetry(event -> log.warn("Retry '{}' attempt #{} failed ({}), retrying in {}ms",
                        name,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "unknown" : event.getLastThrowable().toString(),
                        event.getWaitInterval().toMillis()))
                .onError(event -> log.warn("Retry '{}' exhausted after {} attempts: {}",
                        name,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "unknown" : event.getLastThrowable().toString()));
    }
}
```

- [ ] **Step 2: Compile common**

Run: `mvn -q -pl common -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerLoggingConfig.java
git commit -m "feat(common): log Resilience4j retry events for demoable error behaviour"
```

---

## Task 3: AOP smoke test — prove @Retry activates under Spring Boot 4 (booking-service)

**Files:**
- Test: `microservices/booking-service/src/test/java/com/awbd/cinema/resilience/Resilience4jAnnotationSmokeTest.java`

This is the single most important risk-retirement step: it proves `resilience4j-spring-boot3` annotation aspects actually run under Boot 4 before we depend on them everywhere.

- [ ] **Step 1: Write the test**

Create `microservices/booking-service/src/test/java/com/awbd/cinema/resilience/Resilience4jAnnotationSmokeTest.java`:

```java
package com.awbd.cinema.resilience;

import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the Resilience4j @Retry annotation aspect is active under Spring Boot 4
 * (the one compatibility caveat: the aspects ship in the legacy-named resilience4j-spring-boot3
 * artifact, pulled in transitively by spring-cloud-starter-circuitbreaker-resilience4j).
 */
@SpringBootTest(properties = {
        "resilience4j.retry.instances.smokeTest.max-attempts=3",
        "resilience4j.retry.instances.smokeTest.wait-duration=10ms",
        "resilience4j.retry.instances.smokeTest.retry-exceptions=java.lang.IllegalStateException"
})
@ActiveProfiles("test")
class Resilience4jAnnotationSmokeTest {

    @Autowired
    private FlakyBean flakyBean;

    @Test
    void retryAnnotation_invokesMethodMaxAttemptsTimes_onBoot4() {
        assertThatThrownBy(() -> flakyBean.alwaysFails())
                .isInstanceOf(IllegalStateException.class);

        // 1 initial call + 2 retries == max-attempts(3). Proves the @Retry aspect is wired.
        assertThat(flakyBean.getInvocations()).isEqualTo(3);
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        FlakyBean flakyBean() {
            return new FlakyBean();
        }
    }

    static class FlakyBean {
        private final AtomicInteger invocations = new AtomicInteger();

        @Retry(name = "smokeTest")
        public void alwaysFails() {
            invocations.incrementAndGet();
            throw new IllegalStateException("boom");
        }

        int getInvocations() {
            return invocations.get();
        }
    }
}
```

- [ ] **Step 2: Run the test — expect PASS**

Run: `mvn -q -pl booking-service -am test -Dtest=Resilience4jAnnotationSmokeTest`
Expected: `BUILD SUCCESS`, 1 test passed.

If it FAILS with `invocations == 1` (no retry), the annotation aspect is not active under Boot 4 — STOP and reconsider (e.g. add explicit `@EnableAspectJAutoProxy` or the resilience4j aspect config); do not proceed to the gateway tasks until this passes.

- [ ] **Step 3: Commit**

```bash
git add microservices/booking-service/src/test/java/com/awbd/cinema/resilience/Resilience4jAnnotationSmokeTest.java
git commit -m "test(booking): smoke-test Resilience4j @Retry aspect under Spring Boot 4"
```

---

## Task 4: CatalogServiceGateway + wire-up (booking-service)

**Files:**
- Test: `microservices/booking-service/src/test/java/com/awbd/cinema/clients/CatalogServiceGatewayTest.java`
- Create: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceGateway.java`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClient.java`
- Delete: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClientFallbackFactory.java`
- Delete: `microservices/booking-service/src/test/java/com/awbd/cinema/clients/CatalogServiceClientFallbackFactoryTest.java`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketService/TicketServiceImpl.java`
- Modify: `microservices/booking-service/src/test/java/com/awbd/cinema/services/TicketServiceTest.java`

The gateway's fallback unit test asserts the exact same behaviour the deleted `FallbackFactory` had. We test the **fallback methods directly** (the resilience annotations are exercised by Task 3 and the live demo), so the unit test stays a fast Mockito test.

- [ ] **Step 1: Write the failing gateway test**

Create `microservices/booking-service/src/test/java/com/awbd/cinema/clients/CatalogServiceGatewayTest.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceGatewayTest {

    private CatalogServiceGateway gateway;

    @BeforeEach
    void setUp() {
        // The raw client is unused by the fallback path, so a bare mock is fine.
        gateway = new CatalogServiceGateway(
                mock(CatalogServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void propagatesNotFound_whenCatalogReturns404_withRealMessage() {
        Throwable cause = feign(404, "{\"status\":404,\"message\":\"Screen session not found.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Screen session not found.");
    }

    @Test
    void propagatesConflict_whenCatalogReturns409() {
        Throwable cause = feign(409, "{\"message\":\"Already exists.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Already exists.");
    }

    @Test
    void usesDefaultMessage_whenClientErrorBodyIsUnparseable() {
        Throwable cause = feign(404, "<html>not json</html>");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("The request could not be completed.");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogReturns5xx() {
        Throwable cause = feign(503, "{\"message\":\"boom\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }

    @Test
    void fallsBackToUnavailable_whenCatalogIsUnreachable() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        assertThatThrownBy(() -> gateway.getTicketSetupFallback(1L, 2L, 3L, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }

    @Test
    void bulk_propagatesClientError_whenCatalogReturns404() {
        Throwable cause = feign(404, "{\"message\":\"Screen session not found.\"}");

        assertThatThrownBy(() -> gateway.getTicketSetupsFallback(null, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Screen session not found.");
    }

    @Test
    void bulk_fallsBackToUnavailable_onOutage() {
        Throwable cause = new RuntimeException("Connection refused");

        assertThatThrownBy(() -> gateway.getTicketSetupsFallback(null, cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Catalog service is currently unavailable");
    }
}
```

- [ ] **Step 2: Run it — expect FAIL (compile error: CatalogServiceGateway does not exist)**

Run: `mvn -q -pl booking-service -am test-compile -Dtest=CatalogServiceGatewayTest`
Expected: compilation failure referencing `CatalogServiceGateway`.

- [ ] **Step 3: Create the gateway**

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceGateway.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.BadRequestException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilience boundary for catalog-service calls: Resilience4j retry (transient faults only) +
 * circuit breaker, with a fallback that surfaces real 4xx business errors and degrades to
 * "unavailable" on a genuine outage. Callers inject this instead of {@link CatalogServiceClient}.
 *
 * <p>{@code @Retry} is the outermost aspect, so {@code fallbackMethod} lives on it and fires once,
 * after all retry attempts are exhausted (or immediately for a non-retryable cause such as a 4xx
 * or an open circuit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogServiceGateway {

    private static final String NAME = "catalog-service";

    private final CatalogServiceClient catalogServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getTicketSetupFallback")
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        return catalogServiceClient.getTicketSetup(seatId, roomId, sessionId);
    }

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getTicketSetupsFallback")
    public List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto) {
        return catalogServiceClient.getTicketSetups(dto);
    }

    TicketSetupDTO getTicketSetupFallback(Long seatId, Long roomId, Long sessionId, Throwable cause) {
        // A real 4xx from catalog (session not found, seat not in room, …) surfaces with its
        // true status/message; only a genuine outage falls back to "unavailable".
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}). Cause: {}",
                seatId, roomId, sessionId, cause.toString());
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }

    List<TicketSetupDTO> getTicketSetupsFallback(BulkSaveTicketsDTO dto, Throwable cause) {
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("catalog-service unavailable for bulk ticket-setup. Cause: {}", cause.toString());
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }
}
```

- [ ] **Step 4: Run the gateway test — expect PASS**

Run: `mvn -q -pl booking-service -am test -Dtest=CatalogServiceGatewayTest`
Expected: `BUILD SUCCESS`, 7 tests passed.

- [ ] **Step 5: Drop fallbackFactory from the Feign client and delete the old factory + its test**

In `microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClient.java`, change the annotation:

```java
@FeignClient(
        name = "catalog-service",
        path = "/api/v1",
        fallbackFactory = CatalogServiceClientFallbackFactory.class
)
```

to:

```java
@FeignClient(
        name = "catalog-service",
        path = "/api/v1"
)
```

Then delete the two files:

```bash
git rm microservices/booking-service/src/main/java/com/awbd/cinema/clients/CatalogServiceClientFallbackFactory.java \
       microservices/booking-service/src/test/java/com/awbd/cinema/clients/CatalogServiceClientFallbackFactoryTest.java
```

- [ ] **Step 6: Switch TicketServiceImpl to the gateway**

In `microservices/booking-service/src/main/java/com/awbd/cinema/services/TicketService/TicketServiceImpl.java`:

Change the import:
```java
import com.awbd.cinema.clients.CatalogServiceClient;
```
to:
```java
import com.awbd.cinema.clients.CatalogServiceGateway;
```

Change the field:
```java
    private final CatalogServiceClient catalogServiceClient;
```
to:
```java
    private final CatalogServiceGateway catalogServiceGateway;
```

Change the two call sites:
```java
        TicketSetupDTO setup = catalogServiceClient.getTicketSetup(dto.seatId(), dto.roomId(), dto.screenSessionId());
```
to:
```java
        TicketSetupDTO setup = catalogServiceGateway.getTicketSetup(dto.seatId(), dto.roomId(), dto.screenSessionId());
```
and:
```java
        List<TicketSetupDTO> setups = catalogServiceClient.getTicketSetups(filteredDto);
```
to:
```java
        List<TicketSetupDTO> setups = catalogServiceGateway.getTicketSetups(filteredDto);
```

- [ ] **Step 7: Update TicketServiceTest to mock the gateway**

In `microservices/booking-service/src/test/java/com/awbd/cinema/services/TicketServiceTest.java`:

Change the import `com.awbd.cinema.clients.CatalogServiceClient` to `com.awbd.cinema.clients.CatalogServiceGateway`.

Change the mock field:
```java
    @Mock private CatalogServiceClient catalogServiceClient;
```
to:
```java
    @Mock private CatalogServiceGateway catalogServiceGateway;
```

Replace every remaining occurrence of the identifier `catalogServiceClient` with `catalogServiceGateway` in this file (the `when(...)`, `verify(...)`, and `verifyNoInteractions(...)` calls — the method names and arguments are unchanged because the gateway mirrors the client signatures).

- [ ] **Step 8: Run the booking unit tests for the affected classes — expect PASS**

Run: `mvn -q -pl booking-service -am test -Dtest=CatalogServiceGatewayTest,TicketServiceTest`
Expected: `BUILD SUCCESS`, all tests passed.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(booking): wrap catalog-service in resilient gateway (retry+CB+fallback)"
```

---

## Task 5: UserServiceGateway + wire-up (booking-service)

**Files:**
- Test: `microservices/booking-service/src/test/java/com/awbd/cinema/clients/UserServiceGatewayTest.java`
- Create: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceGateway.java`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClient.java`
- Delete: `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClientFallbackFactory.java`
- Delete: `microservices/booking-service/src/test/java/com/awbd/cinema/clients/UserServiceClientFallbackFactoryTest.java`
- Modify: `microservices/booking-service/src/main/java/com/awbd/cinema/services/OrderService/OrderServiceImpl.java`
- Modify: `microservices/booking-service/src/test/java/com/awbd/cinema/services/OrderServiceTest.java`

**Preserve the existing graceful-degradation behaviour exactly** (confirmed from the deleted `UserServiceClientFallbackFactory`): on a genuine outage, `getLoyaltyPoints` does **not** throw — it returns `new LoyaltyPointsDTO(id, 0)` (treat as 0 points so the booking flow proceeds), and `updateLoyaltyPoints` returns `new LoyaltyPointsDTO(id, dto.loyaltyPoints())` (skip the update, echo the requested value). A real 4xx is still surfaced via the translator.

- [ ] **Step 1: Write the failing gateway test**

Create `microservices/booking-service/src/test/java/com/awbd/cinema/clients/UserServiceGatewayTest.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceGatewayTest {

    private UserServiceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new UserServiceGateway(
                mock(UserServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void getLoyalty_propagatesNotFound_whenUserReturns404() {
        Throwable cause = feign(404, "{\"message\":\"User not found.\"}");

        assertThatThrownBy(() -> gateway.getLoyaltyPointsFallback(1L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void getLoyalty_degradesToZeroPoints_onOutage() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        LoyaltyPointsDTO result = gateway.getLoyaltyPointsFallback(1L, cause);

        assertThat(result.loyaltyPoints()).isZero();
    }

    @Test
    void updateLoyalty_propagatesNotFound_whenUserReturns404() {
        Throwable cause = feign(404, "{\"message\":\"User not found.\"}");

        assertThatThrownBy(() -> gateway.updateLoyaltyPointsFallback(1L, new AdjustLoyaltyPointsDTO(7), cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void updateLoyalty_echoesRequestedValue_onOutage() {
        Throwable cause = new RuntimeException("Connection refused");

        LoyaltyPointsDTO result = gateway.updateLoyaltyPointsFallback(1L, new AdjustLoyaltyPointsDTO(7), cause);

        // Skip the update but echo the requested value so the caller can proceed.
        assertThat(result.loyaltyPoints()).isEqualTo(7);
    }
}
```

- [ ] **Step 2: Run it — expect FAIL (compile error: UserServiceGateway does not exist)**

Run: `mvn -q -pl booking-service -am test-compile -Dtest=UserServiceGatewayTest`
Expected: compilation failure referencing `UserServiceGateway`.

- [ ] **Step 3: Create the gateway**

Create `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceGateway.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience boundary for user-service calls (loyalty points): Resilience4j retry (transient faults
 * only) + circuit breaker. A real 4xx is surfaced via the translator; on a genuine outage the
 * fallback degrades gracefully so the booking flow can still proceed (treat balance as 0 / skip the
 * update). Callers inject this instead of {@link UserServiceClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceGateway {

    private static final String NAME = "user-service";

    private final UserServiceClient userServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getLoyaltyPointsFallback")
    public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
        return userServiceClient.getLoyaltyPoints(id);
    }

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "updateLoyaltyPointsFallback")
    public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
        return userServiceClient.updateLoyaltyPoints(id, dto);
    }

    LoyaltyPointsDTO getLoyaltyPointsFallback(Long id, Throwable cause) {
        // A real 4xx (user not found / bad request) is surfaced; only a genuine outage degrades
        // to 0 points so the booking flow can still proceed.
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("user-service unavailable; treating loyalty points as 0 for user {}. Cause: {}",
                id, cause.toString());
        return new LoyaltyPointsDTO(id, 0);
    }

    LoyaltyPointsDTO updateLoyaltyPointsFallback(Long id, AdjustLoyaltyPointsDTO dto, Throwable cause) {
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("user-service unavailable; skipping loyalty-points update for user {}. Cause: {}",
                id, cause.toString());
        return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
    }
}
```

- [ ] **Step 4: Run the gateway test — expect PASS**

Run: `mvn -q -pl booking-service -am test -Dtest=UserServiceGatewayTest`
Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Drop fallbackFactory from the Feign client and delete the old factory + its test**

In `microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClient.java`, change:

```java
@FeignClient(
        name = "user-service",
        path = "/api/v1",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
```
to:
```java
@FeignClient(
        name = "user-service",
        path = "/api/v1"
)
```

Then:
```bash
git rm microservices/booking-service/src/main/java/com/awbd/cinema/clients/UserServiceClientFallbackFactory.java \
       microservices/booking-service/src/test/java/com/awbd/cinema/clients/UserServiceClientFallbackFactoryTest.java
```

- [ ] **Step 6: Switch OrderServiceImpl to the gateway**

In `microservices/booking-service/src/main/java/com/awbd/cinema/services/OrderService/OrderServiceImpl.java`:

Change the import `com.awbd.cinema.clients.UserServiceClient` to `com.awbd.cinema.clients.UserServiceGateway`.

Change the field:
```java
    private final UserServiceClient userServiceClient;
```
to:
```java
    private final UserServiceGateway userServiceGateway;
```

Replace every occurrence of the identifier `userServiceClient` with `userServiceGateway` in this file (4 call sites: `getLoyaltyPoints` and `updateLoyaltyPoints` inside `createOrder`, `getDiscountPreview`, and `payOrder`). Method names and arguments are unchanged.

- [ ] **Step 7: Update OrderServiceTest to mock the gateway**

In `microservices/booking-service/src/test/java/com/awbd/cinema/services/OrderServiceTest.java`:

Change the import `com.awbd.cinema.clients.UserServiceClient` to `com.awbd.cinema.clients.UserServiceGateway`.

Change the mock field:
```java
    @Mock private UserServiceClient userServiceClient;
```
to:
```java
    @Mock private UserServiceGateway userServiceGateway;
```

Replace every remaining occurrence of the identifier `userServiceClient` with `userServiceGateway` in this file.

- [ ] **Step 8: Confirm OrderIntegrationTest needs no change**

`OrderIntegrationTest` uses `@MockitoBean private UserServiceClient userServiceClient;`. The real `UserServiceGateway` bean delegates to that mocked client, so the integration test keeps working unchanged. Do **not** modify it. (It will be exercised in the full reactor build in Task 8.)

- [ ] **Step 9: Run the affected booking unit tests — expect PASS**

Run: `mvn -q -pl booking-service -am test -Dtest=UserServiceGatewayTest,OrderServiceTest`
Expected: `BUILD SUCCESS`, all tests passed.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(booking): wrap user-service in resilient gateway (retry+CB+fallback)"
```

---

## Task 6: NotificationGateway + wire-up (user-service)

**Files:**
- Test: `microservices/user-service/src/test/java/com/awbd/cinema/clients/NotificationGatewayTest.java`
- Create: `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationGateway.java`
- Modify: `microservices/user-service/src/main/java/com/awbd/cinema/clients/BookingServiceClient.java`
- Delete: `microservices/user-service/src/main/java/com/awbd/cinema/clients/BookingServiceClientFallbackFactory.java`
- Delete: `microservices/user-service/src/test/java/com/awbd/cinema/clients/BookingServiceClientFallbackFactoryTest.java`
- Modify: `microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/AuthServiceImpl.java`
- Modify: `microservices/user-service/src/test/java/com/awbd/cinema/services/AuthServiceTest.java`

The notification fallback is **best-effort**: surface a real 4xx, otherwise log a WARN and **skip silently** (return normally).

- [ ] **Step 1: Write the failing gateway test**

Create `microservices/user-service/src/test/java/com/awbd/cinema/clients/NotificationGatewayTest.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationGatewayTest {

    private NotificationGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new NotificationGateway(
                mock(BookingServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private CreateNotificationDTO sampleDto() {
        return new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "hello", 42L);
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void propagatesClientError_whenBookingReturns400() {
        Throwable cause = feign(400, "{\"message\":\"Malformed notification.\"}");

        assertThatThrownBy(() -> gateway.createNotificationFallback(sampleDto(), cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Malformed notification.");
    }

    @Test
    void skipsSilently_whenBookingIsUnavailable() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        // Best-effort: an outage must NOT break registration — the fallback returns normally.
        assertThatCode(() -> gateway.createNotificationFallback(sampleDto(), cause))
                .doesNotThrowAnyException();
    }
}
```

> Verify the `CreateNotificationDTO` record's component order (`type`, `content`, `userId`) and the `NotificationType` enum constant name against the actual sources before running; adjust `sampleDto()` if they differ.

- [ ] **Step 2: Run it — expect FAIL (compile error: NotificationGateway does not exist)**

Run: `mvn -q -pl user-service -am test-compile -Dtest=NotificationGatewayTest`
Expected: compilation failure referencing `NotificationGateway`.

- [ ] **Step 3: Create the gateway**

Create `microservices/user-service/src/main/java/com/awbd/cinema/clients/NotificationGateway.java`:

```java
package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience boundary for booking-service notification calls: Resilience4j retry (transient faults
 * only) + circuit breaker. Notifications are best-effort, so the fallback surfaces a real 4xx (a
 * genuine bug in the request) but skips silently on a genuine outage rather than failing the caller
 * (e.g. registration must still succeed if notifications are down). Callers inject this instead of
 * {@link BookingServiceClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationGateway {

    private static final String NAME = "booking-service";

    private final BookingServiceClient bookingServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "createNotificationFallback")
    public void createNotification(CreateNotificationDTO dto) {
        bookingServiceClient.createNotification(dto);
    }

    void createNotificationFallback(CreateNotificationDTO dto, Throwable cause) {
        // A real 4xx (e.g. a malformed notification request) is surfaced so the bug is not
        // swallowed; only a genuine booking-service outage is skipped silently.
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("booking-service unavailable; skipping notification of type {} for user {}. Cause: {}",
                dto.type(), dto.userId(), cause.toString());
    }
}
```

- [ ] **Step 4: Run the gateway test — expect PASS**

Run: `mvn -q -pl user-service -am test -Dtest=NotificationGatewayTest`
Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Drop fallbackFactory from the Feign client and delete the old factory + its test**

In `microservices/user-service/src/main/java/com/awbd/cinema/clients/BookingServiceClient.java`, change:

```java
@FeignClient(
        name = "booking-service",
        path = "/api/v1",
        fallbackFactory = BookingServiceClientFallbackFactory.class
)
```
to:
```java
@FeignClient(
        name = "booking-service",
        path = "/api/v1"
)
```

Then:
```bash
git rm microservices/user-service/src/main/java/com/awbd/cinema/clients/BookingServiceClientFallbackFactory.java \
       microservices/user-service/src/test/java/com/awbd/cinema/clients/BookingServiceClientFallbackFactoryTest.java
```

- [ ] **Step 6: Switch AuthServiceImpl to the gateway**

In `microservices/user-service/src/main/java/com/awbd/cinema/services/AuthService/AuthServiceImpl.java`:

Change the import `com.awbd.cinema.clients.BookingServiceClient` to `com.awbd.cinema.clients.NotificationGateway`.

Change the field:
```java
    private final BookingServiceClient bookingServiceClient;
```
to:
```java
    private final NotificationGateway notificationGateway;
```

Change the call site:
```java
        bookingServiceClient.createNotification(notification);
```
to:
```java
        notificationGateway.createNotification(notification);
```

(If `bookingServiceClient.createNotification(...)` is called from more than one place in this file, replace every occurrence.)

- [ ] **Step 7: Update AuthServiceTest to mock the gateway**

In `microservices/user-service/src/test/java/com/awbd/cinema/services/AuthServiceTest.java`:

Change the import `com.awbd.cinema.clients.BookingServiceClient` to `com.awbd.cinema.clients.NotificationGateway`.

Change the mock field:
```java
    @Mock private BookingServiceClient bookingServiceClient;
```
to:
```java
    @Mock private NotificationGateway notificationGateway;
```

Replace every remaining occurrence of the identifier `bookingServiceClient` with `notificationGateway` in this file (the `verify(...)` calls — method name `createNotification` and arguments unchanged).

- [ ] **Step 8: Run the affected user-service unit tests — expect PASS**

Run: `mvn -q -pl user-service -am test -Dtest=NotificationGatewayTest,AuthServiceTest`
Expected: `BUILD SUCCESS`, all tests passed.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(user): wrap booking-service notifications in resilient gateway (retry+CB+fallback)"
```

---

## Task 7: Remove the now-dead CircuitBreakerConfiguration (common)

`CircuitBreakerConfiguration` only customized the Spring Cloud / OpenFeign circuit-breaker factory, which is no longer used (OpenFeign CB is disabled; the benign-4xx ignore rules now live in the `resilience4j.circuitbreaker` YAML from Task 1).

**Files:**
- Delete: `microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerConfiguration.java`
- Delete: `microservices/common/src/test/java/com/awbd/cinema/config/CircuitBreakerConfigurationTest.java`

- [ ] **Step 1: Delete both files**

```bash
git rm microservices/common/src/main/java/com/awbd/cinema/config/CircuitBreakerConfiguration.java \
       microservices/common/src/test/java/com/awbd/cinema/config/CircuitBreakerConfigurationTest.java
```

- [ ] **Step 2: Verify nothing references it**

Run: `grep -rn "CircuitBreakerConfiguration" microservices --include=*.java`
Expected: no output (the logging config is a different class, `CircuitBreakerLoggingConfig`).

- [ ] **Step 3: Compile common — expect PASS**

Run: `mvn -q -pl common -am compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(common): remove dead OpenFeign circuit-breaker customizer"
```

---

## Task 8: Full reactor build + demo verification

**Files:** none (verification only).

- [ ] **Step 1: Build the whole reactor with tests**

Run: `mvn -q -T1C clean verify` (from `microservices/`)
Expected: `BUILD SUCCESS` for every module. This catches any `@WebMvcTest`/slice that fails to load shared `common` beans (per the project memory note) and confirms `OrderIntegrationTest` still passes with the gateway in place.

If a slice test fails to load context referencing a resilience bean, ensure the `resilience4j.*` YAML is present in that service's effective config (config-server for runtime; test profiles use Resilience4j defaults, which are sufficient).

- [ ] **Step 2: Manual demo dry-run (document the exact steps in the PR)**

Bring the stack up, then demonstrate each criterion live:

```bash
# from repo root
docker compose -f docker-compose.microservices.yml up -d
# wait for services to register in Eureka, then:
docker stop catalog-service
```

Trigger a booking flow that calls catalog-service (create a ticket/order via the client UI or a direct API call to booking-service), then watch the logs:

```bash
docker logs -f booking-service
```

Expected ordered log story (proves Retry → CircuitBreaker → Fallback):

```
Retry 'catalog-service' attempt #1 failed (...), retrying in 1000ms
Retry 'catalog-service' attempt #2 failed (...), retrying in 2000ms
Retry 'catalog-service' exhausted after 2 attempts: ...
CircuitBreaker 'catalog-service' recorded a failed call: ...
CircuitBreaker 'catalog-service' state transition CLOSED -> OPEN     (after minimum-number-of-calls)
CircuitBreaker 'catalog-service' rejected a call (circuit is OPEN).  (subsequent calls, fast-fail)
```

The client receives the fallback response ("Catalog service is currently unavailable…"). Then recover:

```bash
docker start catalog-service
# after ~10s (wait-duration-in-open-state) trigger another call:
```
Expected: `CircuitBreaker 'catalog-service' state transition OPEN -> HALF_OPEN` then `HALF_OPEN -> CLOSED`.

- [ ] **Step 3: Confirm the retry-exception whitelist matches reality**

While catalog-service is stopped, confirm the failure thrown is one of the whitelisted types (`feign.RetryableException` for connection-refused, or a 503 `FeignServerException` from the load balancer when no instance is registered) — i.e. the "Retry attempt #N" lines actually appear. If retries do **not** appear, capture the real exception class from the logs and add it to `resilience4j.retry.configs.default.retry-exceptions` in config-server, then `docker compose ... restart` the caller (or trigger a `busrefresh`).

- [ ] **Step 4: Final commit if any config tweak was needed in Step 3**

```bash
git add microservices/config-server/config-repo/application.yml
git commit -m "config: align retry-exceptions with observed outage exception type"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- Resilience4j → used throughout (Tasks 1, 4, 5, 6). ✓
- Retry mechanism → `@Retry` on every gateway + central config (Tasks 1, 4–6); proven under Boot 4 (Task 3). ✓
- Fallback methods → preserved per client in gateway `fallbackMethod`s with identical behaviour (Tasks 4–6). ✓
- Demoable behaviour → retry+CB event logging (Task 2) + documented manual-shutdown demo (Task 8). ✓
- Disable OpenFeign CB + remove dead `CircuitBreakerConfiguration` → Tasks 1, 7. ✓
- Centralized config in config-server → Task 1. ✓
- Full reactor build before pushing (per memory note) → Task 8. ✓

**Placeholder scan:** No TBD/TODO; all code blocks are complete. Two explicit "verify against source" notes (UserService fallback wording; CreateNotificationDTO/NotificationType shape) are deliberate guards, not deferred work.

**Type consistency:** Gateway method signatures mirror the Feign clients exactly (`getTicketSetup`, `getTicketSetups`, `getLoyaltyPoints`, `updateLoyaltyPoints`, `createNotification`); fallback method names referenced in annotations (`getTicketSetupFallback`, `getTicketSetupsFallback`, `getLoyaltyPointsFallback`, `updateLoyaltyPointsFallback`, `createNotificationFallback`) match their definitions and the unit tests that call them directly. Instance names (`catalog-service`, `user-service`, `booking-service`) are consistent between YAML and annotations.
