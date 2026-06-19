# Spring Cloud Config Server — Design

**Date:** 2026-06-19
**Branch:** `config-server`
**Status:** Approved (pending spec review)

## Goal

Add a Spring Cloud Config server to the microservices architecture. It must:

1. Be used by all microservices for configuration.
2. Externalize sensitive configuration (encrypted at rest).
3. Support dynamic refresh of config without restarting services.

## Context

The microservices reactor (`microservices/`, Spring Boot 4.0.7 / Spring Cloud
2025.1.2 / Java 21) currently has modules: `discovery-server` (Eureka),
`common`, `user-service`, `catalog-service`, `booking-service`, `gateway`.

Today each service configures itself via a local
`application.properties`/`application.yml` with `${ENV_VAR}` placeholders.
Secrets (`JWT_SECRET_KEY`, `TMDB_API_KEY`, DB password, bootstrap owner
password) live in `.env` and are injected through
`docker-compose.microservices.yml`. There is no central config and no way to
change a running service's configuration without restarting it.

## Decisions

| Decision | Choice |
|---|---|
| Config backend | Native filesystem (files mounted as a Docker volume) |
| Discovery model | Config-first: services contact config server by direct URL, then register with Eureka |
| Secrets at rest | Symmetric `{cipher}` encryption for `JWT_SECRET_KEY` and `TMDB_API_KEY` only |
| DB password | Plain `${DATABASE_PASSWORD}` env placeholder (Postgres containers need it anyway) |
| Bootstrap owner password | NOT externalized — it is a one-time seed; kept as a local env-backed value, documented in README |
| Dynamic refresh | Spring Cloud Bus over RabbitMQ; `POST /actuator/busrefresh` broadcasts to the whole fleet |
| Primary refresh trigger | The gateway (`http://localhost:8080/actuator/busrefresh`) |
| Demo refresh target | Logging level (`logging.level.com.awbd.cinema`) — refreshes natively, no code changes |

## Architecture

### New module: `config-server`

- New Maven module `microservices/config-server`, same parent POM, Spring Boot
  4.0.7 / Spring Cloud 2025.1.2.
- Port **8888** (Spring Cloud Config convention), mapped to the host so
  `/encrypt` is reachable for authoring ciphertext.
- `@EnableConfigServer`; native backend
  (`spring.profiles.active=native`,
  `spring.cloud.config.server.native.search-locations=file:/config`).
- The config directory is **mounted as a Docker volume**
  (`./microservices/config-server/config:/config:ro`) so files can be edited on
  the host and served on the next fetch with no config-server restart.
- The config server does **not** depend on Eureka, so it can start first.

### Discovery model — config first

Each client service contacts the config server by a fixed URL at startup:

```properties
spring.config.import=configserver:http://config-server:8888
spring.cloud.config.fail-fast=true
```

with `spring-retry` so a client waits/retries for the config server rather than
crashing if it is briefly unavailable. This avoids the chicken-and-egg of
discovering the config server through Eureka.

### Dependency / startup graph

```
config-server (root, no deps)
  → discovery-server + rabbitmq
    → user-service / catalog-service / booking-service
      → gateway
        → client
```

Business services and the gateway gain `depends_on` on `config-server`
(healthy) and `rabbitmq` (healthy).

## Configuration layout

### Stays local in each service

The minimum needed before a service can call the config server:

- `spring.application.name` (the lookup key)
- `spring.config.import=configserver:http://config-server:8888`
- `spring.cloud.config.fail-fast=true` + retry settings

**Exception — user-service** also keeps `bootstrap.owner-*=${BOOTSTRAP_OWNER_*}`
locally (see "Bootstrap owner password" below).

**Exception — discovery-server** stays fully self-configured with its local
`application.yml`. It must start before the config server, so making it a config
client would create a circular dependency.

### Moves to the config server (`/config` volume)

```
config/
  application.yml          # shared by ALL config-client services
  user-service.yml
  catalog-service.yml
  booking-service.yml
  gateway.yml
```

- **`application.yml`** (shared): Eureka client block, `eureka.instance.*` lease
  timers, `management.endpoints.web.exposure.include` (now adding `busrefresh`),
  JPA `show-sql`/`ddl-auto`, logging rolling policy, CSRF/CORS/
  cookie security defaults, OpenFeign circuit-breaker flag, RabbitMQ connection.
- **`<service>.yml`**: only service-specific config — `server.port`,
  `server.servlet.context-path`, datasource URL, that service's secrets, log
  file name, per-service logging levels, and (gateway) the rate-limit + Redis
  block.

Spring merges `application.yml` + `<service>.yml`. Local `${ENV}` placeholders
(DB URL, DB password, Redis host, Eureka URL) still resolve from each
container's environment as they do today.

## Secrets & encryption

### Model — symmetric `{cipher}`

A single key `ENCRYPT_KEY` is known only to the config server (read from its env
var, fed from `.env`). The config server exposes `POST /encrypt` and
`POST /decrypt` backed by that key. Secret values are stored in the config files
as ciphertext tagged with a `{cipher}` prefix; the config server decrypts them
before serving, so clients receive plaintext and need no encryption logic.

### What gets encrypted

Only the two true application secrets:

- `JWT_SECRET_KEY`
- `TMDB_API_KEY`

Stored as e.g.:

```yaml
jwt:
  secret:
    key: '{cipher}AQB4f2c91x8e...'
```

These leave `.env` entirely. The only secret remaining as an env var is
`ENCRYPT_KEY` itself (the root of trust).

### What is NOT encrypted

- **DB password** — stays a plain `${DATABASE_PASSWORD}` env placeholder. The
  Postgres containers need it as plaintext (`POSTGRES_PASSWORD`) and are not
  config clients, so encrypting it for the services would only duplicate the
  value without removing it from `.env`.
- **Bootstrap owner password** — not externalized at all (see below).

### Authoring / rotation flow (manual, one-time, human)

1. Start the config server (with `ENCRYPT_KEY` set).
2. `curl -X POST http://localhost:8888/encrypt -d '<plaintext>'` → ciphertext.
3. Paste `{cipher}<ciphertext>` into the appropriate config file.
4. Remove the plaintext from `.env`.

Rotation later is the same loop followed by `busrefresh` — no restart. Services
never call `/encrypt`; they receive already-decrypted plaintext at runtime.

### Bootstrap owner password — why it is excluded

`StartupListener` reads `bootstrap.owner-password` via `@Value` and fires once on
`ApplicationReadyEvent`, calling `createOwner`, which is create-if-not-exists
(`AuthServiceImpl.createOwner`). Consequences of externalizing/refreshing it:

- A `busrefresh` would **not** change the owner password — the value is consumed
  once at startup by a listener that is not `@RefreshScope`.
- Even on a full restart, an existing owner's password is **not** updated
  (create-if-not-exists); the value only matters against a fresh database.
- A logged-in admin is **not** logged out (auth is JWT/session-based; the DB row
  is untouched).

It is therefore a one-time seed, not operational config. It stays as
`BOOTSTRAP_OWNER_*` env vars in `.env`, with user-service holding the
`bootstrap.owner-*=${BOOTSTRAP_OWNER_*}` mappings locally. The default owner
login is documented in the README for first-run dev convenience.

### Security caveat

The config server also exposes `/decrypt`. On the internal Docker network this is
acceptable for a PoC, but since we map port 8888 to the host for `/encrypt`
convenience, `/decrypt` is also reachable from the host. The README will note
that `/decrypt` should be disabled or the port kept off-host in a real
deployment.

## Dynamic refresh — Spring Cloud Bus over RabbitMQ

- New **`rabbitmq:3-management`** container; management UI mapped `15672:15672`
  (AMQP `5672` stays internal).
- `spring-cloud-starter-bus-amqp` added to all config-client services, the
  gateway, and the config server. RabbitMQ connection config lives in the shared
  `application.yml`.
- **Trigger:** `POST /actuator/busrefresh` to any one node publishes a
  `RefreshRemoteApplicationEvent` over RabbitMQ; every instance of every service
  rebinds. The host only needs to reach **one** node; the fan-out happens
  entirely inside the Docker network — so it also works for future
  load-balanced replicas that have no host port mapping.
- **Primary trigger: the gateway** — `curl -X POST http://localhost:8080/actuator/busrefresh`.
  The gateway is reactive Spring Cloud Gateway with no Spring Security/CSRF, no
  servlet context-path, is already mapped to host `8080`, and its routes
  (`RouteConfig`) do not match `/actuator/**`, so the request is served by the
  gateway's own management endpoint. Business-service endpoints
  (`http://localhost:808x/api/v1/actuator/busrefresh`) remain valid alternates.

### Enabling changes (current blockers)

1. **Expose the endpoint** — add `busrefresh` to
   `management.endpoints.web.exposure.include` (shared `application.yml`).
   Currently only `health,info,metrics,prometheus` are exposed. The local-only
   `refresh` endpoint is intentionally left unexposed — `busrefresh` already
   refreshes the originating node plus the whole fleet, so a single fleet-wide
   trigger keeps the surface minimal and the demo unambiguous.
2. **CSRF-exempt actuator** — add `/actuator/**` to `ignoringRequestMatchers(...)`
   in the three business services' `SecurityConfig`, so the POST is not rejected
   with 403 when CSRF is enabled. (Auth already permits `/actuator/**`; the
   gateway has no CSRF and needs no change.)

### Demo refresh target

Logging level only: edit `logging.level.com.awbd.cinema` in the shared
`application.yml`, `busrefresh`, observe the level change live. Spring Boot's
`LoggingSystem` reacts to the environment-change event, so no `@RefreshScope`
annotations or business-code changes are required.

## Docker Compose changes

- **`config-server` service:** built from the shared image,
  `mvn -o -pl config-server spring-boot:run`, `SERVER_PORT: 8888`, mapped
  `8888:8888`, env `ENCRYPT_KEY`, mounts `./microservices/config-server/config:/config:ro`,
  healthcheck on `/actuator/health`, no `depends_on`.
- **`rabbitmq` service:** `rabbitmq:3-management`, `15672:15672`, healthcheck via
  `rabbitmq-diagnostics ping`.
- **Business services + gateway:** add `depends_on` on `config-server` (healthy)
  and `rabbitmq` (healthy); add `SPRING_RABBITMQ_HOST: rabbitmq`. Existing env
  (DB URL, DB password, Eureka URL, Redis) is unchanged.
- **`.env` / `.env.example`:** add `ENCRYPT_KEY`; remove `JWT_SECRET_KEY` and
  `TMDB_API_KEY` from the service-facing vars; keep `DATABASE_PASSWORD`
  (Postgres + services) and `BOOTSTRAP_OWNER_*` (local seed). `.env.example`
  documents `ENCRYPT_KEY` with a placeholder.

## Testing & verification

- **Build:** `mvn -o -pl config-server -am compile`, then the full reactor build.
- **Config-server slice test:** context loads with the `native` profile; a
  `WebTestClient`/`TestRestTemplate` hit on `/{service}/default` returns merged
  config and shows a `{cipher}` value **decrypted** in the response (proves
  encryption works end to end).
- **Client smoke (manual, documented):** full
  `docker compose -f docker-compose.microservices.yml up`; confirm each service
  fetches config at startup, all register in Eureka, the app works end-to-end.
- **Refresh demo (manual, documented in README):** edit
  `logging.level.com.awbd.cinema` → `curl -X POST http://localhost:8080/actuator/busrefresh`
  → observe the level change across services (and the message in the RabbitMQ
  UI), no restart.
- **Existing tests:** the three services' security/integration tests must still
  pass; test profiles must not require the config server
  (`spring.cloud.config.enabled=false` or an optional import in tests).

## Out of scope / future

- **Load balancing (#16):** multi-instance scaling. Spring Cloud Bus is being
  added now precisely so refresh works across replicas when that task lands.
- Git or Vault backend; asymmetric (keystore) encryption.
- Authentication on the config server's own endpoints.
