# Circuit Breaker Resilience Enhancement — Design

**Date:** 2026-06-21
**Branch:** `enhance-circuit-breakers`
**Status:** Approved (brainstorming complete)

## Goal

Enhance the inter-service resilience of the microservices platform so it fully
satisfies these criteria:

- **Resilience4j** (already in use) as the resilience library.
- **Retry mechanism** — currently missing.
- **Fallback methods** — already present; preserved and consolidated.
- **Demoable error behaviour** — make failures visible in the logs so they can
  be demonstrated by manually stopping a downstream service.

## Current state

- Stack: **Spring Boot 4.0.7**, **Spring Cloud 2025.1.2**, Java 21.
- `spring-cloud-starter-circuitbreaker-resilience4j:5.0.2` transitively provides
  **Resilience4j 2.3.0**, including `resilience4j-spring-boot3` (the `@Retry` /
  `@CircuitBreaker` AOP aspects) and `resilience4j-retry`. **No new dependency is
  required** — this is the Spring-Cloud-curated, Boot-4-compatible pairing.
- Circuit breaking is wired *implicitly* through OpenFeign's auto-integration
  (`spring.cloud.openfeign.circuitbreaker.enabled=true` + `fallbackFactory=…`),
  which **cannot apply Resilience4j retry**.
- Three Feign clients, each with a `FallbackFactory`:
  | Feign client | service | fallback behaviour |
  |---|---|---|
  | `CatalogServiceClient` | booking-service | surface real 4xx, else "unavailable" |
  | `UserServiceClient` | booking-service | surface real 4xx, else "unavailable" |
  | `BookingServiceClient` (notifications) | user-service | surface real 4xx, else skip silently |
- `FeignClientErrorTranslator` (in `common`) maps downstream 4xx → domain exceptions.
- `CircuitBreakerConfiguration` (in `common`) customizes the Spring Cloud factory
  to ignore benign 4xx.
- `CircuitBreakerLoggingConfig` (in `common`) logs circuit-breaker events at WARN.

### Gaps

- **No retry** — calls fail/fall back immediately on transient faults.
- **No easy way to demo** retry → circuit-open → fallback behaviour.

## Design

### 1. Architecture: explicit resilience wrappers

Make the resilience boundary explicit and owned by us instead of implicit in
OpenFeign:

- **Disable OpenFeign's circuit-breaker integration**
  (`spring.cloud.openfeign.circuitbreaker.enabled=false`) and remove
  `fallbackFactory=` from the three `@FeignClient` interfaces. The Feign clients
  become thin, raw HTTP callers that let `FeignException` / `RetryableException`
  propagate.
- Introduce **one resilience wrapper `@Component` per Feign client** — the single
  place where retry, circuit breaker, and fallback live:

  | Wrapper (new) | wraps | lives in |
  |---|---|---|
  | `CatalogServiceGateway` | `CatalogServiceClient` | booking-service |
  | `UserServiceGateway` | `UserServiceClient` | booking-service |
  | `NotificationGateway` | `BookingServiceClient` | user-service |

  Each public method carries
  `@Retry(name="<service>")` + `@CircuitBreaker(name="<service>", fallbackMethod="…")`
  and delegates to the raw client.
- **Callers inject the gateway** instead of the raw client:
  `TicketServiceImpl` + `OrderServiceImpl` (booking), `AuthServiceImpl` (user-service).

**Why a wrapper, not annotations on the Feign interface:** Resilience4j's AOP
advises Spring beans reliably; stacking its aspect directly on the Feign proxy is
fragile. The wrapper is the standard, testable pattern.

The three `FallbackFactory` classes are **removed**; their logic (surface real 4xx
via `FeignClientErrorTranslator`, degrade only on genuine outage) **moves into each
wrapper's `fallbackMethod`**. `FeignClientErrorTranslator` is reused unchanged.

### 2. Configuration (centralized in config-server)

All tuning lives in `microservices/config-server/config-repo/application.yml` under
`resilience4j.*`, one instance per downstream service name
(`catalog-service`, `user-service`, `booking-service`).

**Retry** (per instance):
- `max-attempts: 3`, `wait-duration: 1s`, exponential backoff `multiplier: 2`
  (waits ~1s then ~2s).
- **Retry only transient faults**: connection failures (`feign.RetryableException`)
  and 5xx (`feign.FeignException$FeignServerException`). **Never retry 4xx**.
  (Exact retry-exception class names confirmed during implementation via the AOP
  smoke test.)

**CircuitBreaker** (per instance):
- `sliding-window-size: 10`, `minimum-number-of-calls: 5`,
  `failure-rate-threshold: 50`.
- `wait-duration-in-open-state: 10s` (short, so the demo shows recovery),
  `permitted-number-of-calls-in-half-open-state: 3`,
  `automatic-transition-from-open-to-half-open-enabled: true`.
- **Ignore the benign 4xx** (`FeignException.NotFound` / `.BadRequest` / `.Conflict`
  / `.UnprocessableEntity`) — same intent as today's `CircuitBreakerConfiguration`,
  now declarative.

Because nothing uses the OpenFeign/Spring Cloud circuit-breaker factory anymore,
`CircuitBreakerConfiguration` and its test are **removed** (dead code).

### 3. Fallback semantics (preserved per client)

Relocated into wrapper `fallbackMethod`s, behaviour unchanged:
- **catalog / user gateways** (data the booking needs): surface real 4xx via the
  translator; on genuine outage / open circuit, throw
  `BadRequestException("… currently unavailable …")`.
- **notification gateway** (non-critical): surface real 4xx; on outage / open
  circuit, **log a WARN and skip silently** (notifications are best-effort).

### 4. Logging & demoability

`CircuitBreakerLoggingConfig` (in `common`) is **extended to also bind
`RetryRegistry` events** (`onRetry`, `onError`), alongside the existing
circuit-breaker event logging. All at WARN — console-visible; the file appender
threshold stays ERROR.

**Demo procedure (manual shutdown):**
1. `docker stop catalog-service`.
2. Call a booking endpoint that needs catalog (e.g. create order/ticket).
3. Observe the ordered log story on the console:
   ```
   Retry 'catalog-service' attempt #1 failed (ConnectException), retrying in 1000ms
   Retry 'catalog-service' attempt #2 failed (ConnectException), retrying in 2000ms
   Retry 'catalog-service' exhausted after 3 attempts
   CircuitBreaker 'catalog-service' recorded a failed call: ...
   CircuitBreaker 'catalog-service' state transition CLOSED -> OPEN
   CircuitBreaker 'catalog-service' rejected a call (circuit is OPEN).
   ```
4. `docker start catalog-service`; after ~10s the next call drives
   `OPEN -> HALF_OPEN -> CLOSED`.

### 5. Testing

- **Wrapper unit tests** (replace the three `FallbackFactory` tests): mock the raw
  client to throw; assert 4xx is surfaced via the translator and outage degrades
  correctly (fail "unavailable" / skip silently).
- **AOP smoke test**: one `@SpringBootTest` asserting `@Retry` invokes the delegate
  `max-attempts` times — proves the Resilience4j annotation aspects activate under
  **Spring Boot 4** (the one compatibility caveat).
- **Existing service tests** (`TicketServiceTest`, `OrderServiceTest`,
  `AuthServiceTest`, `OrderIntegrationTest`) updated to inject/mock the gateways.
- Full reactor build before pushing (shared `common` web beans must load in every
  service slice).

## Out of scope (YAGNI)

- `@Bulkhead`, `@RateLimiter`, `@TimeLimiter` — not required by the criteria;
  Feign's own connect/read timeouts remain.
- A chaos/fault-injection endpoint — the demo uses manual `docker stop`.

## Files affected

**common**
- `config/CircuitBreakerLoggingConfig.java` — extend with retry event logging.
- `config/CircuitBreakerConfiguration.java` + its test — **removed**.
- `config/FeignClientErrorTranslator.java` — unchanged (reused).

**booking-service**
- `clients/CatalogServiceClient.java`, `clients/UserServiceClient.java` — drop
  `fallbackFactory=`.
- `clients/CatalogServiceClientFallbackFactory.java`,
  `clients/UserServiceClientFallbackFactory.java` (+ tests) — **removed**.
- New: `clients/CatalogServiceGateway.java`, `clients/UserServiceGateway.java`
  (+ unit tests).
- `services/TicketService/TicketServiceImpl.java`,
  `services/OrderService/OrderServiceImpl.java` — inject gateways.
- Tests updated accordingly.

**user-service**
- `clients/BookingServiceClient.java` — drop `fallbackFactory=`.
- `clients/BookingServiceClientFallbackFactory.java` (+ test) — **removed**.
- New: `clients/NotificationGateway.java` (+ unit test).
- `services/AuthService/AuthServiceImpl.java` — inject gateway.
- Tests updated accordingly.

**config-server**
- `config-repo/application.yml` — `resilience4j.retry.*` +
  `resilience4j.circuitbreaker.*` instances; set
  `spring.cloud.openfeign.circuitbreaker.enabled=false`.
