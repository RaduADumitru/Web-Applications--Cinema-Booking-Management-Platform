# Absolute Cinema - Cinema Booking and Management Platform

The application is a Cinema Booking & Management System platform that lets users browse available movies, select screenings and book tickets, and lets administrators manage the platform's content (movies, rooms, schedule).
The system was initially designed as a monolithic application, later to be decomposed into a microservices-based architecture. The split is based on the application's main responsibilities: user management, movie management, and booking management.

# Requirements

## User Management
The system must allow user registration.
The system must allow user authentication.
The system must allow users to log out.
The system must manage roles.
The system must restrict access to certain features based on role.

![Register](./images/register.png)


## Movie Management
The system must allow viewing the list of movies.
The system must allow viewing the details of a movie.
Administrators must be able to: add movies, edit movies, delete movies.
The system must allow associating movies with screenings.
The system must allow searching and sorting movies.

![Movie List](./images/movie-list.png)

## Screening and Room Management
The system must allow creating screenings for movies.
The system must allow associating a screening with a cinema room.
The system must manage the available seats in a room.
The system must allow viewing the available seats for a screening.

![Screenings](./images/screenings.png)

## Booking Management
Users must be able to: select a screening, select available seats, view their own bookings, cancel bookings.
The system must allow creating a booking.
The system must generate tickets for each booked seat.
The system must prevent double-booking of the same seat.

![Screenings](./images/bookings.png)

# Microservice Architecture

The platform is split into three business services (`user`, `catalog`, `booking`),
each owning its **own** Postgres database, fronted by an API gateway and supported
by platform services (service discovery, centralized config), shared infrastructure
(Redis, RabbitMQ) and an observability stack (Prometheus/Grafana/Loki/Zipkin).

The Angular client only ever talks to the **gateway** (`/api/v1`). The gateway
load-balances across the **2 replicas** of each business service via Eureka
(`lb://<service>`). Services call each other over the Docker network with Feign
(`/internal/**` endpoints, not exposed through the gateway).

```mermaid
flowchart TB
    client["Angular client<br/>:4200"]

    subgraph edge ["Edge"]
        gateway["API Gateway<br/>Spring Cloud Gateway :8080"]
    end

    subgraph platform ["Platform services"]
        discovery["discovery-server<br/>Eureka :8761"]
        config["config-server<br/>Spring Cloud Config :8888"]
    end

    subgraph business ["Business services — 2 replicas each"]
        user["user-service"]
        catalog["catalog-service"]
        booking["booking-service"]
    end

    subgraph data ["Datastores"]
        userdb[("user-db<br/>Postgres")]
        catalogdb[("catalog-db<br/>Postgres")]
        bookingdb[("booking-db<br/>Postgres")]
        redis[("Redis<br/>cache + rate limiter")]
    end

    rabbit["RabbitMQ<br/>Spring Cloud Bus :15672"]

    subgraph observability ["Observability"]
        prometheus["Prometheus<br/>:9090"]
        grafana["Grafana<br/>:3000"]
        loki["Loki<br/>:3100"]
        promtail["Promtail"]
        zipkin["Zipkin<br/>:9411"]
    end

    %% request path
    client -->|REST /api/v1| gateway
    gateway -->|lb:// load-balanced| user
    gateway -->|lb://| catalog
    gateway -->|lb://| booking
    gateway -->|rate limiter| redis

    %% per-service data ownership
    user --> userdb
    catalog --> catalogdb
    booking --> bookingdb
    catalog --> redis
    booking --> redis

    %% inter-service Feign (/internal/** + synchronous saga steps)
    user -. Feign .-> booking
    booking -. Feign .-> catalog
    booking -. "Feign (saga steps)" .-> user

    %% discovery + config
    gateway & user & catalog & booking -. register / discover .-> discovery
    gateway & user & catalog & booking -. fetch config .-> config

    %% config hot-reload over Spring Cloud Bus
    gateway & user & catalog & booking -. busrefresh (Cloud Bus) .-> rabbit

    %% observability
    gateway & user & catalog & booking -. traces .-> zipkin
    prometheus -. scrape /actuator/prometheus .-> gateway & user & catalog & booking & discovery
    promtail -. container logs .-> loki
    grafana --> prometheus
    grafana --> loki
```

Solid arrows are the synchronous request path; dotted arrows are
discovery/config, Spring Cloud Bus config refresh, and observability flows.

# API Documentation

The full REST API is described by an **OpenAPI 3.0.3** specification at
[`api-docs.yaml`](./api-docs.yaml). The contract is **identical
for the monolith and the microservices stack**. Service-to-service `/internal/**` endpoints are intentionally excluded, as
they are not part of the public API.

The rendered, browsable documentation is deployed on
[GitHub Pages](https://raduadumitru.github.io/Web-Applications--Cinema-Booking-Management-Platform/#/).

# Running the Application

## Prerequisites

- Docker + Docker Compose v2.
- A `.env` file at the repo root (copy from `.env.example`).
- Set a TMDB API key in `.env`, along with a sufficiently long JWT Secret Key

## Start Monolith

```bash
docker compose -f docker-compose.yml up --build
```

The monolith backend is published on **host port 8081** (`docker-compose.yml` maps
`8081:8080`, so the app listens on 8080 inside the container) and is reachable at
**http://localhost:8081/api/v1**; the Angular client runs at **http://localhost:4200**.

### Stop

```bash
docker compose -f docker-compose.yml down        # keep data
docker compose -f docker-compose.yml down -v     # also drop DB volumes
```
## Start Microservices

```bash
docker compose -f docker-compose.microservices.yml up --build
```

First run is slow: the shared image compiles the whole reactor (`mvn install`), then
each service container compiles + boots its module. Watch the healthchecks; the
gateway only starts routing once `user-service`, `catalog-service`, and
`booking-service` report healthy.

### Stop
```bash
docker compose -f docker-compose.microservices.yml down        # keep data
docker compose -f docker-compose.microservices.yml down -v     # also drop DB volumes
```

## Ports

| URL                          | Component                                                             |
|------------------------------|-----------------------------------------------------------------------|
| http://localhost:8080/api/v1 | **API gateway** (what the frontend uses)                              |
| http://localhost:4200        | Angular client  (Default bootstrap user login: `admin`/`Redacted1!`)  |
| http://localhost:8761        | Eureka dashboard (`discovery-server`)                                 |
| http://localhost:8888        | config-server (Spring Cloud Config; `/encrypt`, `/<service>/default`) |
| http://localhost:15672       | RabbitMQ management UI (Spring Cloud Bus; `guest`/`guest`)            |
| http://localhost:9411        | Zipkin distributed-tracing UI                                         |
| http://localhost:3000        | Grafana dashboards (`admin`/`admin`)                                  |
| http://localhost:9090        | Prometheus (metrics & scrape targets)                                 |
| http://localhost:3100        | Loki log API (queried through Grafana)                                |

Internal `/internal/**` endpoints are **not** routed by the gateway — they are reachable
only over the Docker network (service-to-service Feign calls).

The three business services (`user-service`, `catalog-service`, `booking-service`) run **2
instances each** and are reachable only through the gateway on `http://localhost:8080`. 
See **Load Balancing** below.

# Relational Schema

![ERD](./images/ERD.png)

# Security & Access Control

User-facing authentication is JWT-based over an HttpOnly `jwt` cookie (a separate
refresh-token cookie supports silent renewal). Credentials are checked against the
database through a `CustomUserDetailsService` / `CustomAuthenticationProvider`, and
passwords are stored with **BCrypt**.

Authorization uses a **role hierarchy** — `OWNER > STAFF > USER` — so higher roles
inherit the permissions of lower ones. Endpoints are guarded with method-level
`@PreAuthorize`:

- **OWNER** — user administration (`/user/all`, promote, delete users).
- **STAFF** — content management (movies, screen-sessions, tickets, order listing).
- **USER** — browsing and a user's own bookings/notifications.

**CSRF protection** is enabled by default with a cookie-stored token
(`CookieCsrfTokenRepository`), toggleable via `SECURITY_CSRF_ENABLED`. Repeated failed
logins are throttled by a `LoginAttemptService` (`SECURITY_MAX_ATTEMPTS`, default 5).
Login and logout are exposed at `POST /api/v1/auth/login` and `POST /api/v1/auth/logout`.

This user-facing layer is independent of the **Service-to-Service Security** described
below, which protects `/internal/**` calls with a different token type.

# Validation & Error Handling

Every write endpoint validates its request body with **Bean Validation** — `@Valid` on
the controller plus JSR-380 constraints on the DTOs (`@NotBlank`, `@Email`, `@Size`,
`@Pattern`, `@FutureOrPresent`, nested `@Valid`, and a custom `@PasswordMatch` for
registration).

A global `@RestControllerAdvice` turns failures into consistent JSON: validation errors
return **422** with a per-field message map, and domain exceptions map to the right
status (`400`, `401/403`, `404`, `429`, `500`). Each body carries `timestamp`, `status`,
`message`, and optional `details`.

The Angular client mirrors the key rules with reactive-form validators (required, email,
length, pattern, cross-field password match), so users get immediate feedback before
submitting.

# Pagination & Sorting

List endpoints are paginated with Spring Data `Pageable` (most default to
`@PageableDefault(size = 20)`), covering orders, movies, screen-sessions, tickets,
ticket-info, offers, notifications, rooms, seats, and users. Sorting is applied with
`Sort` (e.g. users by `username`, movies by upload date), and request filters (status,
movie, room, availability, …) compose with paging. Pages are serialized through a small
`RestPage` wrapper so the JSON page shape stays stable for the client.

# Caching (Redis)

Frequently-read data is cached in **Redis** via Spring Cache (`@Cacheable` /
`@CacheEvict` / `@Caching`). Each cache has its own TTL tuned to how fast the data
changes — e.g. `single_movies` 2h, `admin_movies` 15m, `order_lists` 5m — and writes
evict the affected caches so reads stay consistent. Cache operations are **best-effort**:
a `CacheErrorHandler` logs and swallows Redis failures so requests fall through to the
database instead of erroring when Redis is unavailable. Caching is active in the monolith
and in the catalog/booking services. (Redis also backs the gateway rate limiter — see
**API Gateway**.)

# Logging

Logging uses **SLF4J + Logback**. Each service writes to its own file
(`logs/<service>.log`) with a rolling policy (10 MB/file, 100 MB cap), and the file
appender threshold is **`ERROR`**, so errors are retained separately from console output.
Levels are set centrally (`com.awbd.cinema` at INFO; raise to DEBUG live via `busrefresh`
— see Centralized Configuration). Cross-cutting request logging is provided by a servlet
filter in the monolith (`RequestLoggingFilter`) and a `LoggingGlobalFilter` at the gateway,
both recording method, path, status and latency; Resilience4j retry/circuit-breaker events
are logged for observability.

# Service Discovery (Eureka)

The microservices register themselves with a **Netflix Eureka** service registry
(`discovery-server`, host port **8761**) instead of using hardcoded inter-service
URLs. The gateway routes to services by name (`lb://user-service`, etc.) and the
Feign clients resolve their targets by service name through the registry.

## Eureka dashboard

With the stack running, open **http://localhost:8761**. The "Instances currently
registered with Eureka" table lists every running app:
`USER-SERVICE`, `CATALOG-SERVICE`, `BOOKING-SERVICE`, and `GATEWAY`. Stop a
service (`docker compose -f docker-compose.microservices.yml stop catalog-service`)
and refresh — its instances disappear from the table; start it again
(`docker compose -f docker-compose.microservices.yml start catalog-service`) and they
re-register automatically. This is the live proof that services discover each other
with no static configuration.

## What the logs show

- On startup each app logs its registration, e.g.
  `DiscoveryClient_CATALOG-SERVICE/... - registration status: 204`
  (`com.netflix.discovery` at INFO).
- On each inter-service call, the caller logs the name→instance selection, e.g.
  the gateway and booking-service log LoadBalancer choosing an instance for
  `catalog-service` (`org.springframework.cloud.loadbalancer` at DEBUG).

Because resolution is by service name, no URL changes are needed to move or scale
a service — the registry always reports its current location.

# Centralized Configuration (Spring Cloud Config)

All four microservices fetch their configuration at startup from a **Spring Cloud
Config server** (`config-server`, host port **8888**) rather than carrying full
local config. Each service keeps only a tiny bootstrap (its name +
`spring.config.import=configserver:...`); everything else — shared defaults in
`application.yml` plus a per-service `<service>.yml` — lives in
`microservices/config-server/config-repo/` and is served centrally. The server uses the
**native** (filesystem) backend, and that directory is mounted into the container
as a volume, so edits on the host are served on the next fetch without rebuilding.

## Encrypted secrets

Sensitive values are stored as `{cipher}`-encrypted text and decrypted by the
config server before being served (clients receive plaintext and need no crypto
of their own). Encryption is symmetric, keyed by `ENCRYPT_KEY` from `.env`.
`JWT_SECRET_KEY` ships this way in the committed `application.yml`.

To (re)encrypt a value, with the config server running:

```bash
curl -X POST http://localhost:8888/encrypt -d 'the-secret-value'
```

Copy the output and paste it into the relevant config file as `'{cipher}<output>'`.
To rotate a secret, re-encrypt and then `busrefresh` (below) — no restart.

> **Security note:** the config server also exposes `/decrypt`. That's convenient
> locally (port 8888 is mapped to the host), but in a real deployment you should
> disable `/decrypt` or keep 8888 off the host.

## Live configuration refresh (no restart)

Config changes propagate to running services via **Spring Cloud Bus over
RabbitMQ** — no restart, no redeploy. Edit a served value, then broadcast a
refresh to the whole fleet by POSTing to the gateway:

```bash
curl -X POST http://localhost:8080/actuator/busrefresh
```

Every service receives the event over RabbitMQ and rebinds. Watch the broadcast
in the RabbitMQ management UI at **http://localhost:15672** (default `guest`/`guest`).

**Test:** the business services' `JwtAuthenticationFilter` logs one line per
request at DEBUG (`JWT filter processing GET /api/v1/... (jwt cookie absent)`).
`logging.level.com.awbd.cinema` defaults to `INFO`, so that line is hidden. Set
it to `DEBUG` in `microservices/config-server/config-repo/application.yml`, run
the `busrefresh` above, then hit any business endpoint through the gateway
(e.g. `curl http://localhost:8080/api/v1/movies`) — the line now appears in the
catalog-service logs, with no restart. Set it back to `INFO` + `busrefresh` and
it's gone again.

### RabbitMQ management UI

The bus broker (`rabbitmq:3-management`) exposes a web console at
**http://localhost:15672**.

- **Username / password:** `guest` / `guest` (the image default — no credentials
  are configured in `docker-compose.microservices.yml`, and the services connect
  with Spring's default `guest`/`guest`).
- Under **Queues** you'll see one queue per running instance, named
  `springCloudBus.<service>-<uuid>` (e.g. `springCloudBus.catalog-service-…`), each
  bound to the `springCloudBus` exchange. Watching their message rates while you
  run `busrefresh` shows the refresh event fanning out to every service.

# Service-to-Service Security

Internal endpoints (`/internal/**`) are authenticated with a short-lived
service JWT, not just network isolation. On every outgoing Feign call the
calling service mints a ~60s token (`typ=SERVICE`, `sub=<service-name>`,
signed with the shared `jwt.secret.key`) and sends it as
`Authorization: Bearer …`. Each service enforces a dedicated `/internal/**`
security chain that validates the token and requires `ROLE_SERVICE`. This is
independent of the user's `jwt` cookie (which still authenticates public
endpoints) — the `typ` claim keeps the two token kinds non-interchangeable.
The TTL is tunable via `SERVICE_TOKEN_TTL_SECONDS` (default 60).

## Seeing it work (logs)

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

Negative proof — `/internal/**` is **not reachable from the host** at all: the
business services publish no host ports (they run as load-balanced replicas),
and the gateway does not route `/internal/**`. To show the rejection you must
call the endpoint from *inside* the Docker network, bypassing the gateway by
hitting the service name directly (`curl` is present in the service image):

```bash
# No token -> 401
docker compose -f docker-compose.microservices.yml exec gateway \
  curl -i "http://catalog-service:8080/api/v1/internal/ticket-setup?seatId=1&roomId=1&sessionId=1"
# Forged token -> 401, and catalog-service logs "Rejecting internal request with invalid service token"
docker compose -f docker-compose.microservices.yml exec gateway \
  curl -i -H "Authorization: Bearer garbage" \
  "http://catalog-service:8080/api/v1/internal/ticket-setup?seatId=1&roomId=1&sessionId=1"
```

(From the host, `curl http://localhost:8080/api/v1/internal/ticket-setup` just
returns `404` — the gateway has no such route — which is itself a form of
proof that internal endpoints aren't externally exposed.)

# Load Balancing (multiple instances)

The stack runs **2 instances of each business service** (`user-service`, `catalog-service`,
`booking-service`) via Docker Compose `deploy.replicas`. Client-side load balancing is provided by
**Spring Cloud LoadBalancer** (default round-robin) in two places:

- the **gateway** routes (`lb://user-service`, …) distribute external requests across instances;
- **Feign** clients distribute inter-service (`/internal/**`) calls across instances by service name.

Each instance stamps an `X-Served-By: <service>@<host>:<port>` response header and logs
`served <method> <uri> by <id>` for every non-actuator request.

## See gateway load balancing (no auth needed)

Register the `demo` user first (see **Smoke test** above), then loop the public login endpoint and
watch the `X-Served-By` header alternate across the two user-service instances:

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -D - -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"demo","password":"Password123!"}' \
  | grep -i '^X-Served-By'
done
```

PowerShell:

```powershell
1..10 | ForEach-Object {
  (Invoke-WebRequest -Uri http://localhost:8080/api/v1/auth/login -Method Post `
    -ContentType 'application/json' `
    -Body '{"username":"demo","password":"Password123!"}').Headers['X-Served-By']
}
```

You should see two distinct `user-service@<host>:8080` ids appear across the 10 requests.

## How it works

Each instance registers with Eureka (`prefer-ip-address: true`, so replicas get distinct ids).
The gateway resolves `lb://<service>` routes and Feign resolves `@FeignClient(name = "<service>")`
through Eureka, and **Spring Cloud LoadBalancer** picks an instance per call using its default
round-robin strategy. No load-balancer configuration is added — multiplicity plus the
`X-Served-By` header and request logging are all that's needed to demonstrate it.

# Distributed Transactions and the Saga Pattern

One of the more advanced engineering aspects of the project is how it handles the booking workflow across service boundaries. When a user completes a purchase, the
operation involves multiple steps across the Booking Service and the Catalog Service - reserving seats, creating an order, processing payment, and awarding loyalty
points. In a distributed system, any one of these steps can fail, which could leave the data in an inconsistent state.

To address this, the Booking Service implements the Saga orchestration pattern through two coordinated sagas: CreateOrderSaga and PayOrderSaga. The orchestrator
drives the workflow step by step and, if any step fails, triggers compensating actions to undo the work already done - for example, releasing reserved seats if
payment fails. This ensures the system remains consistent even in the face of partial failures, without relying on a distributed database transaction.

# API Gateway

**Spring Cloud Gateway** is the single public entry point (`:8080`) and provides:

- **Centralized routing** — path-based routes to `lb://user-service`,
  `lb://catalog-service`, `lb://booking-service`, load-balanced across replicas.
- **Rate limiting** — a Redis-backed `RedisRateLimiter` (default 50 req/s replenish,
  100 burst), keyed per client IP, applied to every route. If Redis is unreachable the
  limiter fails open within ~250 ms instead of stalling requests.
- **Request/response filtering** — a `LoggingGlobalFilter` logs each request (method,
  path, status, latency), and CORS is terminated here at the edge (disabled on the
  downstream services).

`/internal/**` paths are deliberately not routed (see **Service-to-Service Security**).

# Resilience & Fault Tolerance

Inter-service calls are guarded with **Resilience4j**. Each Feign client is wrapped by a
gateway component annotated with `@CircuitBreaker` + `@Retry` and a fallback method:

- `booking-service → catalog-service` (ticket setup),
- `booking-service → user-service` (loyalty points),
- `user-service → booking-service` (welcome notification).

The shared config (`config-repo/application.yml`) retries only transient faults (3
attempts, exponential backoff) and opens the breaker at a 50% failure rate over a 10-call
window, while ignoring benign 4xx so business errors don't trip it. Fallbacks degrade
gracefully (e.g. treat the loyalty balance as 0, skip a best-effort notification) — except
in saga steps, which fail fast so the orchestrator compensates. Retry and breaker state
transitions are logged by `CircuitBreakerLoggingConfig`.

# Monitoring & Observability

The stack ships with a full observability layer, all started together by
`docker-compose.microservices.yml`:

- **Metrics** — every service exposes Spring Boot **Actuator**
  (`/actuator/health`, `/metrics`, `/prometheus`, `/info`). **Prometheus** (`:9090`)
  scrapes each replica via DNS service discovery, and **Grafana** (`:3000`,
  `admin`/`admin`) auto-provisions a Prometheus/Loki datasource plus the per-service
  dashboard at `infrastructure/grafana/dashboards/dashboard.json` — pick a service from
  the **Application** dropdown to scope the panels.
- **Health checks** — Docker healthchecks gate startup ordering on each service's
  Actuator health endpoint.
- **Distributed tracing** — Micrometer Tracing + Brave export spans to **Zipkin**
  (`:9411`); trace context propagates across gateway → service → Feign calls.
- **Log aggregation** — **Promtail** tails container logs into **Loki** (`:3100`),
  queryable from Grafana alongside the metrics.

# Configuration Profiles & Testing

**Profiles.** The default configuration targets **PostgreSQL** for development and
runtime; a `test` profile (`application-test.yml` in each module) swaps in an **H2
in-memory** database (`create-drop`) and disables Eureka, the Config Server, and the
Cloud Bus, so tests run standalone with no external infrastructure.

**Tests.** The suite uses **JUnit 5 + Mockito** across the monolith and every
microservice: service-layer unit tests (`@ExtendWith(MockitoExtension.class)`), controller
slices (`@WebMvcTest` + MockMvc), end-to-end integration tests (`@SpringBootTest`,
`@ActiveProfiles("test")`), plus dedicated saga-compensation and circuit-breaker tests.
Run them per module with `./mvnw test` (monolith) or `mvn verify` (microservices reactor).

# Continuous Integration (CI)

A **GitHub Actions** workflow (`.github/workflows/maven-ci.yml`) runs on every push and
pull request to `main` and `dev`. It builds and tests both code bases on JDK 21 with
Maven caching — the monolith via `./mvnw clean test` and the microservices reactor via
`mvn -B -ntp clean verify` (H2-backed, no external services required) — gating the
pull-request workflow described in **Contributing**.

# AI-Assisted Development

AI tooling was used throughout the project in two ways:

- **Early phase — GitHub Copilot.** The initial monolith work was driven largely through
  GitHub Copilot, including automated Copilot reviews on raised PRs. This was scaled back once the
  available Copilot quota for students was drastically reduced.
- **Later phase — Claude Code with the Superpowers plugin.** The microservices work
  followed a **spec-driven development** model using the
  [Superpowers](https://github.com/obra/superpowers) plugin for Claude Code: high complexity features
  began with a written **spec** (requirements + design) that was reviewed, then turned into
  one or more step-by-step **implementation plans** executed and verified incrementally. These
  artifacts are committed under [`docs/superpowers`](./docs/superpowers) — design specs in
  [`docs/superpowers/specs`](./docs/superpowers/specs) and the corresponding execution
  plans in [`docs/superpowers/plans`](./docs/superpowers/plans) — so each major change can
  be traced from its spec to its plan to the resulting code.

# Contributing

All changes must be integrated through **pull requests** and **pass CI checks** — do not push directly to
the long-lived branches.

- **Target the `dev` branch.** Open every pull request against `dev`, never against
  `main`.
- **Merge via squash merge.** Pull requests into `dev` are integrated with a
  **squash merge**, so each PR lands as a single commit on `dev`.
- **Promotion to `main` is manual.** `dev` is merged into `main` manually via **standard merge**. 
- Do not open PRs from feature branches directly to `main`.

## Team Member Main Contributions

- @Radush02: Monolith feature development, bulk of frontend functionality implementation
- @AnghelAnaMaria: Monolith feature development, Monitoring stack and Saga pattern
- @RaduADumitru: Microservices architecture (Config server, load balancing, circuit breakers, distributed security)
