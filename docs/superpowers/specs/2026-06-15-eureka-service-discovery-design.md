# Eureka Service Discovery — Design

## 1. Overview

This spec adds **Netflix Eureka service discovery** to the existing microservices
stack (`microservices/`), satisfying the coursework requirement:

- A **functional service registry**.
- **Inter-service communication via REST** (the existing Feign clients), switched
  from hardcoded URLs to **name-based** resolution through the registry.
- A **demonstration that services discover each other automatically**, via the
  Eureka dashboard and application-level logs.

It builds directly on `2026-06-14-microservices-extraction-design.md` (§11, which
explicitly deferred Eureka: *"gateway uses static routes for now; a later phase
adds Eureka and switches routes to `lb://service-name`"*).

This remains a **PoC**: working end-to-end beats production-grade. Guiding
principle: smallest change that makes discovery functional and demonstrable.

## 2. Goals & Constraints

- Stand up a single Eureka registry as a new module in the existing reactor.
- Make `user-service`, `catalog-service`, `booking-service`, and `gateway`
  register with Eureka and resolve each other **by service name** — no hardcoded
  inter-service URLs remain.
- Preserve all current behaviour and API contracts (Angular frontend unaffected;
  DTO/JSON shapes unchanged; `/internal/**` endpoints still reachable only on the
  Docker network).
- Keep the existing explicit gateway route table and per-route rate-limit filters.
- The whole stack still starts via a single
  `docker compose -f docker-compose.microservices.yml up`.
- **Scope boundary:** this task delivers discovery + name-based resolution with
  **one instance per service**. Running multiple instances and demonstrating
  traffic spread is the separate **Load Balancing (#16)** task, which builds on
  this. Spring Cloud LoadBalancer arrives here only transitively (it is what
  resolves a service name to an instance); no multi-instance config is added.

## 3. Scope

**In scope:**
- New `discovery-server` module (Eureka Server) + its compose service.
- Eureka client setup for the 4 apps.
- Feign clients switched to name-based resolution (drop `url`, add `path`).
- Gateway routes switched to `lb://`.
- Compose wiring (env, `depends_on`, healthcheck).
- Demonstration: dashboard + logging config + README section.
- Test adjustments + a discovery-server context-load test.

**Out of scope (separate tasks):**
- Multi-instance scaling + load-balancing demonstration (#16).
- Spring Cloud Config Server (#1).
- Prometheus/Grafana/tracing (#5).

## 4. Components

### 4.1 New module: `microservices/discovery-server` (Eureka Server)
- Maven module, `packaging=jar`, added as `<module>discovery-server</module>` to
  `microservices/pom.xml`.
- Dependency: `spring-cloud-starter-netflix-eureka-server` (version managed by the
  existing `spring-cloud-dependencies` BOM, `2025.1.2`). Plus
  `spring-boot-starter-actuator` for the healthcheck.
- `DiscoveryServerApplication` annotated `@EnableEurekaServer`.
- `application.yml`:
  - `server.port: ${SERVER_PORT:8761}`
  - `spring.application.name: discovery-server`
  - `eureka.client.register-with-eureka: false`
  - `eureka.client.fetch-registry: false`
  - `management.endpoints.web.exposure.include: health,info`
- The Eureka **dashboard** is served at `/` (the registry's home page) — this is
  the primary visual demonstration surface.
- No `common`, DB, Redis, or security dependencies — the registry is standalone.

### 4.2 The 4 apps become Eureka clients
`user-service`, `catalog-service`, `booking-service`, `gateway` each get:
- Dependency `spring-cloud-starter-netflix-eureka-client` in their own `pom.xml`
  (not in `common`, to keep the dependency explicit per service and avoid forcing
  it on every `common` consumer).
- Discovery config (in each app's existing config file — `.properties` for
  user/catalog/booking, `.yml` for gateway):
  - `eureka.client.service-url.defaultZone = ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}`
  - `eureka.instance.prefer-ip-address = true` — each container advertises its
    Docker-network IP, which peer containers can actually reach. (Without this,
    Eureka advertises the container hostname/ID, which is not resolvable by other
    containers — the classic Eureka-in-Docker failure.)
- Registration uses each app's **existing** `spring.application.name`
  (`user-service` / `catalog-service` / `booking-service` / `gateway`), so the
  registry IDs already match the Feign client `name`s and gateway route ids — no
  renaming needed.
- `@EnableDiscoveryClient` is auto-activated by the starter; the annotation may be
  added on each main class for explicitness but is not required.

### 4.3 Feign clients → name-based resolution
The three existing `@FeignClient` interfaces currently hardcode the target URL:

| Client (in service) | Today | Change |
|---|---|---|
| `CatalogServiceClient` (booking) | `name="catalog-service", url="${services.catalog.url}"` | drop `url`, add `path="/api/v1"` |
| `UserServiceClient` (booking) | `name="user-service", url="${services.user.url}"` | drop `url`, add `path="/api/v1"` |
| `NotificationServiceClient` (user) | `name="booking-service", url="${services.booking.url}"` | drop `url`, add `path="/api/v1"` |

Removing `url` makes Feign resolve the target **by name** via Eureka + Spring
Cloud LoadBalancer. **`path = "/api/v1"` is required** because Eureka registers
only host:port — not the `server.servlet.context-path=/api/v1` — so without it the
resolved call would miss the context-path prefix and 404. With `path="/api/v1"`,
a mapping like `@GetMapping("/internal/ticket-setup")` resolves to
`http://<instance-ip>:8080/api/v1/internal/ticket-setup`, exactly as today.

The fallback classes (`*Fallback`) and `@CircuitBreaker`/`@Retry` behaviour are
unchanged.

The now-unused properties are removed:
- `services.catalog.url`, `services.user.url` from
  `booking-service/.../application.properties`.
- `services.booking.url` from `user-service/.../application.properties`.
- Corresponding entries in `src/test/resources/application.properties` for both
  services.

### 4.4 Gateway routes → `lb://`
In `gateway/.../config/RouteConfig.java`, each route's `.uri(...)` changes from the
injected static URI to a load-balanced scheme:

| Route | Today | Change |
|---|---|---|
| `user-service` | `.uri(userUri)` | `.uri("lb://user-service")` |
| `catalog-service` | `.uri(catalogUri)` | `.uri("lb://catalog-service")` |
| `booking-service` | `.uri(bookingUri)` | `.uri("lb://booking-service")` |

The `@Value("${services.*.uri}")` injections and the `services:` block in
`gateway/application.yml` are removed. **No path rewrite is needed:** the gateway
already forwards the full incoming path (`/api/v1/...`), which matches each
service's context-path. The path predicates and the per-route
`RequestRateLimiter` filter are untouched. The reactive `lb://` scheme is served
by `spring-cloud-starter-loadbalancer` (transitive via the eureka client starter).

### 4.5 Docker Compose (`docker-compose.microservices.yml`)
- **New service `discovery-server`:**
  - Same shared `microservices/Dockerfile` and `maven_repo`/source-volume pattern
    as the other services.
  - `command: sh -c "mvn -o -pl discovery-server spring-boot:run"`
  - `SERVER_PORT: 8761`, `ports: ["8761:8761"]`, on `microservices-network`.
  - Healthcheck: `curl -f http://localhost:8761/actuator/health`.
- **Each of the 4 apps:**
  - Add env `EUREKA_SERVER_URL: http://discovery-server:8761/eureka/`.
  - Add `depends_on: discovery-server: { condition: service_healthy }`
    (in addition to existing DB/redis deps).
  - Remove the replaced env vars: `CATALOG_SERVICE_URL`, `USER_SERVICE_URL`,
    `BOOKING_SERVICE_URL` (booking/user services) and `USER_SERVICE_URI`,
    `CATALOG_SERVICE_URI`, `BOOKING_SERVICE_URI` (gateway).

## 5. Demonstration (dashboard + application logs)

Per requirement "demonstrate that services discover each other automatically":

- **Eureka dashboard** at `http://localhost:8761`: lists all four registered
  instances (`USER-SERVICE`, `CATALOG-SERVICE`, `BOOKING-SERVICE`, `GATEWAY`)
  live. Stopping/starting a container shows it deregister/re-register.
- **Application-level logs:** raise log levels in each client app so the discovery
  lifecycle is visible:
  - `com.netflix.discovery: INFO` — "DiscoveryClient ... registering service..." /
    "registration status: 204" on startup.
  - `org.springframework.cloud.loadbalancer: DEBUG` — logs the name→instance
    selection on each inter-service Feign call.
- **README:** a new "Service Discovery (Eureka)" section in the root `README.md`
  documents the registry URL, what the dashboard shows, and which log lines prove
  auto-discovery and name-based resolution.

## 6. Entity, Frontend, API impact

**None.** No entities, DTOs, controller paths, JSON shapes, or frontend code
change. The gateway still listens on `:8080`, services still serve `/api/v1`,
`/internal/**` is still unreachable through the gateway and now resolved
service-to-service by name over the same Docker network.

## 7. Testing

- **`discovery-server`**: a context-load test (`@SpringBootTest` with the Eureka
  server self-registration disabled) verifying the app starts.
- **Existing Feign tests** (fallback/circuit-breaker, request/response shape):
  remain valid; test profiles set `eureka.client.enabled=false` (and
  `spring.cloud.discovery.enabled=false` where needed) so the suite runs without a
  live registry. The Feign clients in tests resolve via the test config rather
  than Eureka.
- **Gateway test** (`GatewayApplicationTest`): adjusted for the removed
  `services.*.uri` properties; `eureka.client.enabled=false` in the test profile.
- No new automated discovery integration test is required — the demonstration is
  the dashboard + logs per §5. (An automated discovery assertion is a possible
  future addition but out of scope here.)

## 8. Risks / Notes

- **Startup ordering:** apps depend on `discovery-server` being healthy, but
  Eureka registration is also resilient to a briefly-unavailable registry (clients
  retry). `depends_on: service_healthy` makes the common case clean.
- **First-call latency:** Eureka's registry/cache refresh can add a few seconds
  before a freshly-started instance is discoverable. Acceptable for a PoC; noted
  in the README.
- **LoadBalancer present but single-instance:** name resolution now flows through
  Spring Cloud LoadBalancer with one instance per service. Multi-instance
  balancing is intentionally left to task #16.
