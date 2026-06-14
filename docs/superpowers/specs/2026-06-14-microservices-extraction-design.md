# Microservices Extraction — Design

## 1. Overview

This spec covers splitting the current monolithic `cinema` Spring Boot backend
into 3 independent microservices, as the foundation for the optional
"Database Web Applications" coursework requirements (40% of grade, 13 numbered areas
covering config server, service discovery, load balancing, API gateway,
monitoring, distributed security, resilience, design patterns, NoSQL/caching,
micro-frontends, CI/CD, and AI agents).

This is explicitly a **PoC**: working end-to-end beats production-grade. The
guiding principle throughout is "smallest change that preserves current
behaviour and unblocks the remaining requirements."

## 2. Goals & Constraints

- Split the backend into **3 microservices**, each with its **own
  PostgreSQL database**.
- Preserve current functionality and API contracts (the Angular frontend's
  JSON expectations must not change).
- Lay groundwork for the remaining 12 requirement areas (gateway, resilience,
  JWT-based distributed security, caching, a Feign-based communication graph
  suitable for a Saga writeup, etc.) without implementing all of them now.
- **The existing monolith (`cinema/`) is preserved untouched.** The new
  microservices architecture lives in a **completely separate location** in
  the repo. Both can be run independently (not simultaneously) via separate
  Docker Compose files. Both the monolith and the new gateway can use port
  `8080`.
- Everything (microservices + their databases + Redis + frontend) starts
  together via a single `docker compose` invocation.

## 3. Scope

**In scope (this spec):**
- 3-service split: `user-service`, `catalog-service`, `booking-service`.
- A shared `common` library module.
- A minimal `gateway` service (static routing, basic rate limiting and a
  request/response logging filter) — partial coverage of requirement #4.
- Claims-based JWT auth shared across services — groundwork for #6.
- 3 Feign integrations, each with a Resilience4j circuit breaker + fallback —
  groundwork for #2 and #7.
- Per-service PostgreSQL databases + shared Redis cache — groundwork for #9
  (Redis already counts as the NoSQL/caching layer; cache split preserves
  existing behaviour).
- Basic Actuator health endpoints (used by Docker healthchecks) — partial
  groundwork for #5.
- `docker-compose.microservices.yml` to run the whole new stack.

**Explicitly deferred** (see §11): Eureka/service discovery & load balancing
(#2 full, #3), Spring Cloud Config Server (#1), Prometheus/Grafana/tracing
(#5 full), deeper resilience scenarios (#7 full), formal Saga/CQRS/Event
Sourcing documentation (#8), MongoDB (#9 NoSQL DB beyond Redis), micro-frontends
(#10), CI/CD (#11), AI agents (#12, #13).

## 4. Repository Layout

```
/cinema/                              # existing monolith — UNTOUCHED
/client/                              # existing Angular app — UNTOUCHED
/docker-compose.yml                   # existing monolith compose — UNTOUCHED
/microservices/                       # NEW — multi-module Maven reactor
  pom.xml                             # parent, packaging=pom
  Dockerfile                          # one shared Dockerfile (see §8.2)
  common/                             # shared library (jar)
  user-service/
  catalog-service/
  booking-service/
  gateway/
/docker-compose.microservices.yml     # NEW
/.env.example                         # extended with new DB-name vars
```

Running the monolith: `docker compose -f docker-compose.yml up` (unchanged).
Running the microservices: `docker compose -f docker-compose.microservices.yml up`.
Both expose the backend on `http://localhost:8080/api/v1`, so the Angular
frontend (`environment.ts` → `apiUrl: http://localhost:8080/api/v1`) works
against either without modification.

## 5. Service Decomposition

Approach: **Identity / Catalog / Booking+Notifications** — chosen because it
groups entities by which ones share foreign keys today, minimizing
cross-service joins, while still producing a rich 3-way Feign communication
graph (see §6) for the discovery/resilience requirements.

### 5.1 `user-service` (container port 8080, host 8081)
- Entity: `User`.
- Auth: registration, login, JWT issuance (`AuthController`, `AuthServiceImpl`,
  `JwtUtil` token generation).
- `LoginAttemptServiceImpl` (Caffeine-based, in-memory — no Redis needed).
- Bootstrap owner creation (`StartupListener` + `bootstrap.owner-*` env vars).
- `UserController` (`/user/**`).
- Exposes internal endpoints for booking-service: `GET`/`PATCH
  /internal/users/{id}/loyalty-points` (§6.3 ②).
- No Redis dependency.

### 5.2 `catalog-service` (container port 8080, host 8082)
- Entities: `Genre`, `Movie`, `Room`, `Seat`, `SeatCategory`, `ScreenSession`,
  `SessionInfo`. None of these reference `User`/`Order`/`Ticket`/`Notification`
  — they move over **with no internal changes**.
- TMDB integration (`themoviedbapi`, `tmdb.api.key`) stays here entirely.
- Controllers: `MovieController` (`/movies/**`), `RoomController`
  (`/rooms/**`), `SeatController` (`/seats/**`), `ScreenSessionController`
  (`/screen-sessions/**`).
- Redis-backed caches: `admin_movies`, `public_movie_lists`, `single_movies`,
  `seat_lists`, `single_seat`, `screen_session_lists`, `movie_session_lists`,
  `single_screen_sessions`, `room_lists`, `single_room`.
- Exposes one internal endpoint for booking-service (`/internal/ticket-setup`,
  see §6.3 ①).

### 5.3 `booking-service` (container port 8080, host 8083)
- Entities: `Order`, `Ticket`, `TicketInfo`, `Offer`, `PointsSpend`,
  `Notification`.
- `NotificationScheduler` (movie-reminder cron job) — rewritten, see §7.3.
- Controllers: `OrderController` (`/orders/**`), `TicketController`
  (`/tickets/**`), `TicketInfoController` (`/ticket-info/**`),
  `OfferController` (`/offers/**`), `NotificationController`
  (`/notifications/**`).
- Redis-backed caches: `notifications`, `user_notifications`, `offer_lists`,
  `single_offers`, `ticket_lists`, `single_ticket`, `ticket_infos`,
  `single_ticket_info`, `single_orders`, `order_lists`, `user_orders`,
  `user_past_orders`, `user_discount_previews`.
- Exposes one internal endpoint for user-service: `POST /internal/notifications`
  (§6.3 ③).

### 5.4 `gateway` (container port 8080, host 8080)
Spring Cloud Gateway with **static routes** (no service discovery yet — that's
deferred to a later phase per the "minimal gateway now" decision). Each route
matches a path prefix and forwards to the owning service's container; since
every service keeps `server.servlet.context-path=/api/v1` unchanged, routes
need no path rewriting:

| Path prefix | Routed to |
|---|---|
| `/api/v1/auth/**`, `/api/v1/user/**` | `user-service` |
| `/api/v1/movies/**`, `/api/v1/rooms/**`, `/api/v1/seats/**`, `/api/v1/screen-sessions/**` | `catalog-service` |
| `/api/v1/orders/**`, `/api/v1/tickets/**`, `/api/v1/ticket-info/**`, `/api/v1/offers/**`, `/api/v1/notifications/**` | `booking-service` |

`/internal/**` paths on any service are **not** registered in the gateway —
see §6.4.

Two extra filters satisfy the rest of requirement #4 cheaply:
- A global `RequestRateLimiter` (Spring Cloud Gateway's built-in in-memory
  rate limiter) applied to all routes with a generous default limit.
- A simple `GlobalFilter` that logs request/response method, path, and status
  (request/response filtering demo).

### 5.5 `common` (shared library, jar)
Holds code needed by 2+ services, to avoid duplication:
- `Role` enum + role-hierarchy config (`ROLE_OWNER > ROLE_STAFF > ROLE_USER`),
  reused by every service's `@PreAuthorize`/`SecurityConfig`.
- `JwtUtil` (claims constants, signing/validation — generation used only by
  `user-service`, validation used by all).
- Stateless `JwtAuthenticationFilter` (claims-based, no DB lookup — see §6.1).
- `FeignAuthInterceptor` (forwards the `Cookie` header — see §6.2).
- Shared exceptions (`NotFoundException`, `BadRequestException`,
  `AlreadyExistsException`, `InvalidFieldException`, `UnauthenticatedException`,
  `TooManyRequestsException`) + a `GlobalExceptionHandler` (`@ControllerAdvice`).
- `RestPage<T>` pagination wrapper.
- `CacheProperties` config class (each service supplies its own
  `app.cache.caches` map in its own `application.yml`).
- CORS/CSRF config helpers (`SecurityCorsProperties` + base filter setup).
- DTOs for the 3 Feign contracts in §6.3 (`TicketSetupDTO`, `LoyaltyPointsDTO`,
  `CreateNotificationDTO`), shared by caller and callee so both compile
  against the same type.

## 6. Cross-Service Communication & Security

### 6.1 Claims-based JWT
At login, `user-service` embeds `userId` and `role` (in addition to the
existing `sub`=username and `typ` claims) into the JWT, signed with the same
`jwt.secret.key` shared by all services (reused from existing config/env).

The `JwtAuthenticationFilter` (now in `common`, used by all 3 services)
becomes **fully stateless**: it reads the `jwt` cookie, validates the
signature/expiry/`typ=ACCESS`, and builds the
`UsernamePasswordAuthenticationToken` **directly from the claims** — no
`CustomUserDetailsService` / DB lookup. Each service then applies its own
normal `@PreAuthorize` rules against that authentication, exactly as today.

### 6.2 Auth propagation between services
A `FeignAuthInterceptor` (in `common`), registered on every Feign client,
forwards the incoming request's `Cookie` header to the outgoing Feign call.
This means a downstream service sees the same JWT cookie and applies its own
`JwtAuthenticationFilter` + `@PreAuthorize` normally — no separate
service-to-service token scheme needed.

### 6.3 The three Feign integrations
These form a 3-way communication graph across all services — useful both for
the "services discover/communicate" requirement (#2) and for the circuit
breaker requirement (#7, "minimum 2 services").

**① `booking-service` → `catalog-service`: ticket setup (admin operation)**
`TicketServiceImpl.createTicket` currently loads `Seat`, `Room`,
`ScreenSession` via JPA to validate the seat-belongs-to-room and
session-scheduled-in-room relationships, and checks for an existing ticket.
This becomes a single Feign call,
`GET /internal/ticket-setup?seatId=&roomId=&sessionId=`, to catalog-service,
which performs those same relationship checks and returns a `TicketSetupDTO`
snapshot (seat row/number/zone/extra-fee/extra-points, movie title, session
date/start-time/points — see §7.2). `@CircuitBreaker` + `@Retry`; on failure
the fallback throws a clear "catalog service unavailable" error (this is an
admin-only write operation — no degraded mode makes sense).

**② `booking-service` ↔ `user-service`: loyalty points**
`OrderServiceImpl.createOrder` reads `user.getLoyaltyPoints()` (for the
discount preview / `useDiscount` flag) and, if spent, resets it to 0.
`OrderServiceImpl.payOrder` adds the order's earned `loyaltyPoints` back to
the user. Both become Feign calls to `user-service`'s
`GET /internal/users/{id}/loyalty-points` and
`PATCH /internal/users/{id}/loyalty-points`. `@CircuitBreaker` + fallback: if
user-service is unreachable, treat loyalty points as unavailable for that
request (no discount applied / points not awarded) rather than failing order
creation or payment — documented as a PoC simplification and flagged as the
natural candidate for a future Saga writeup (#8).

**③ `user-service` → `booking-service`: registration notification**
`AuthServiceImpl.register()` currently creates an `EMAIL_VERIFICATION`
`Notification` directly via `NotificationRepository` (both entities were in
the same DB). This becomes a Feign call from `user-service` to
`POST /internal/notifications` on `booking-service`, reusing the existing
`CreateNotificationDTO(type, content, userId)` shape. `@CircuitBreaker` +
fallback: log a warning and continue — registration succeeds even if the
welcome notification fails, since it's non-critical.

### 6.4 `/internal/**` convention
Each `/internal/**` endpoint is `permitAll` in its service's `SecurityConfig`
(no JWT required — needed because ③ fires during registration, before any
JWT exists) **and is not registered in the gateway's route table** (§5.4), so
external clients cannot reach it. Protection is Docker-network-isolation only.
This is an explicit PoC simplification, flagged as a candidate for
service-to-service auth hardening in a future phase.

## 7. Entity & Database Changes

### 7.1 Per-service entity changes
- **`catalog-service`**: `Genre`, `Movie`, `Room`, `Seat`, `SeatCategory`,
  `ScreenSession`, `SessionInfo` — **no internal changes**; none of them
  reference `User`/`Order`/`Ticket`/`Notification`.
- **`user-service`**: `User` — drop the `orders`/`notifications`
  `@OneToMany` back-references (those entities no longer share this DB).
  Otherwise unchanged.
- **`booking-service`**:
  - `Order.user` (`@ManyToOne User`) → `Order.userId` (`Long`, no FK).
    `OrderDTO.from()`: `o.getUser().getId()` → `o.getUserId()` — **JSON shape
    unchanged**.
  - `Notification.user` (`@ManyToOne User`) → `Notification.userId` (`Long`,
    no FK). `Notification.order` stays a real FK (same DB).
    `NotificationDTO.from()`: `n.getUser().getId()` → `n.getUserId()` —
    **JSON shape unchanged**.
  - `Ticket.seat` / `.room` / `.screenSession` (`@ManyToOne` →
    catalog-service entities) → plain `seatId` / `roomId` / `sessionId`
    (`Long`, unique constraint preserved as plain columns) **plus** the
    denormalized snapshot fields from §7.2. `Ticket.ticketInfo` stays a real
    FK (same DB). `TicketDTO.from()`: `t.getSeat().getId()` →
    `t.getSeatId()` etc — **JSON shape unchanged**.
  - `TicketInfo`, `Offer`, `PointsSpend` — unchanged internally.

This plain-ID + local-snapshot pattern is the template for any other minor
cross-references discovered during implementation.

### 7.2 Denormalization: `Ticket` snapshot fields
At ticket-creation time (§6.3 ①), `booking-service` calls catalog-service
**once** to validate and fetch a pricing/display snapshot, stored directly on
the `Ticket` row: `seatRow`, `seatNumber`, `seatZone`, `extraFee`,
`extraPoints`, `movieTitle`, `sessionDate`, `sessionStartTime`,
`sessionPoints`. This means `createOrder` (the hot path, called on every
purchase) needs **zero** catalog calls — all pricing and notification-text
data is read from local `Ticket` fields, exactly preserving current
output/logic.

### 7.3 `NotificationScheduler` rewrite
Today, `sendMovieReminders()` joins `ScreenSession`/`Movie` (would be
catalog-service) with `Ticket`/`Order` (booking-service) to build reminder
text. With the §7.2 snapshot fields already on `Ticket`, this becomes a pure
booking-service-local query:
`ticketRepository.findBySessionDateAndOrderIsNotNull(tomorrow)`, grouped by
`order.getUserId()`, with reminder text built from
`ticket.getMovieTitle()` / `ticket.getSessionStartTime()`. **No cross-service
call needed at all** — the snapshot pattern pays off here too.

## 8. Infrastructure: Docker Compose

### 8.1 `docker-compose.microservices.yml` — services

| Container | Image/Build | Host port | Notes |
|---|---|---|---|
| `user-db` | `postgres:15` | — | volume `user_db_data`, db = `USER_DB_NAME` |
| `catalog-db` | `postgres:15` | — | volume `catalog_db_data`, db = `CATALOG_DB_NAME` |
| `booking-db` | `postgres:15` | — | volume `booking_db_data`, db = `BOOKING_DB_NAME` |
| `redis` | `redis:7-alpine` | — | shared by catalog-service + booking-service (disjoint cache-name sets, no collisions) |
| `user-service` | build (see 8.2) | 8081 | no Redis dependency |
| `catalog-service` | build | 8082 | |
| `booking-service` | build | 8083 | |
| `gateway` | build | **8080** | matches `environment.ts` `apiUrl` |
| `client` | existing `./client` build | 4200 | `API_URL=http://localhost:8080/api/v1` |

Each Postgres/Redis container gets the same `pg_isready`/healthcheck pattern
as the existing `docker-compose.yml`; each Spring Boot container exposes
`/actuator/health` (basic Actuator dependency added to the parent pom) used by
a `curl`-based healthcheck, replacing the current `pgrep -f spring-boot`
check — also a small head start on requirement #5.

### 8.2 Build strategy
One shared `microservices/Dockerfile` (mirrors the existing monolith
Dockerfile's "run from source via Maven" style — `maven:3.9.9-eclipse-temurin-21`
base image, no pre-built jars). Each service's compose entry reuses this same
Dockerfile/image, mounts the whole `microservices/` tree as a volume (plus a
shared `maven_repo` volume for the `.m2` cache, like today), sets
`working_dir: /workspace` (the reactor root), and runs:

```
mvn -pl <module> -am spring-boot:run
```

`-am` ("also make") builds the `common` module first within the same reactor
before running the target service — no separate install step required.

### 8.3 `.env.example` additions
Reuses existing `JWT_SECRET_KEY`, `TMDB_API_KEY`, `BOOTSTRAP_OWNER_*`,
`SECURITY_*`, and `DATABASE_USER`/`DATABASE_PASSWORD` (same credentials reused
across all 3 new DB containers — just 3 new database-name vars needed):

```
USER_DB_NAME=user_db
CATALOG_DB_NAME=catalog_db
BOOKING_DB_NAME=booking_db
```

### 8.4 Cache config split
`application.yml`'s `app.cache.caches` map splits along the lines in §5.2/§5.3
— `catalog-service` gets the movie/room/seat/session entries, `booking-service`
gets the ticket/order/offer/notification entries, `user-service` defines no
Redis caches (Caffeine login-attempt cache only, already in-memory and
service-local).

## 9. Frontend

**No frontend code changes.** Both backends listen on `localhost:8080`,
share the `/api/v1` context path, and produce identical JSON (DTO shapes are
preserved exactly per §7). Switching between architectures is purely a choice
of which `docker compose` file to run.

## 10. Testing

The existing `cinema/src/test/**` suite (~28 files) stays untouched with the
monolith. Each new module gets its own `src/test/java` tree, populated by
adapting the existing tests for the classes that moved there:

- `user-service`: Auth*, User*, LoginAttempt*, JWT/security filter tests
  (adapted to the new claims-based, stateless filter).
- `catalog-service`: Movie*, Room*, Seat*, ScreenSession*, TMDB/Redis config
  tests.
- `booking-service`: Order*, Ticket*, TicketInfo*, Offer*, Notification*,
  Scheduler*, Redis config tests.

New tests are added for the 3 Feign integrations' fallback behaviour
(circuit-breaker-open scenarios), plus one integration-style test per
contract verifying the request/response shape against the `common` DTOs.

## 11. Deferred to Future Phases

Out of scope for this spec — each becomes its own future design/plan, building
on this foundation:

- **Eureka service discovery + Spring Cloud LoadBalancer + multi-instance
  scaling** (req #2 full, #3) — gateway uses static routes for now; a later
  phase adds Eureka and switches routes to `lb://service-name`.
- **Spring Cloud Config Server** (req #1) — each service currently uses its
  own `application.yml`/env vars; a later phase centralizes configuration
  with dynamic refresh.
- **Prometheus + Grafana + distributed tracing** (req #5 full) — basic
  Actuator health endpoints are included now (§8.1); dashboards and tracing
  (Zipkin/Jaeger) come later.
- **Deeper Resilience4j scenarios** (req #7 full) beyond the 3 fallbacks in
  §6.3 — additional tuning and explicit error-behaviour demos.
- **Formal Saga/CQRS/Event Sourcing documentation** (req #8) — the loyalty
  points flow (§6.3 ②) is the flagged Saga candidate.
- **MongoDB** (req #9 NoSQL beyond Redis) — Redis caching is preserved as-is;
  notifications stay in Postgres.
- **Micro-frontends, CI/CD, AI agents (dev & runtime)** (req #10–13) — bonus
  items, separate later phases.
