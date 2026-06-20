# Client-Side Load Balancing Demo (Spring Cloud LoadBalancer)

## Goal

Make the platform's existing client-side load balancing **runnable with 2+ instances per
service** and **observable/testable**. Minimum 2 instances per service must be demonstrable.

## Premise

Spring Cloud LoadBalancer is already the active mechanism in this project — it comes transitively
with the Eureka client + Spring Cloud Gateway + OpenFeign, and is already in use:

- **Gateway** routes with `lb://user-service`, `lb://catalog-service`, `lb://booking-service`
  (`microservices/gateway/.../config/RouteConfig.java`).
- **Feign clients** resolve by service name through Eureka and load-balance automatically when
  more than one instance is registered.

Therefore this work does **not** wire load balancing from scratch and does **not** change the LB
strategy (Spring Cloud LoadBalancer's default round-robin is kept). It adds the ability to run
multiple instances and to see/verify that requests are distributed across them.

## Decisions (from brainstorming)

| Topic                            | Decision                                                                          |
|----------------------------------|-----------------------------------------------------------------------------------|
| Run multiple instances           | Docker Compose replicas (`deploy.replicas` / `--scale`)                           |
| Observe gateway LB               | `X-Served-By` response header + per-instance container **logs**                   |
| Observe Feign (inter-service) LB | Per-instance container **logs**                                                   |
| LB scope demonstrated            | Gateway LB **and** Feign LB                                                       |
| LB strategy                      | Keep default round-robin (no custom config)                                       |
| Test deliverable                 | **Documented commands** in the existing root `README.md`                          |
| New endpoints                    | **None** — no `/lb-demo`, no `whoami`, no Feign interceptor, no new gateway route |

## Relevant existing facts (verified)

- All services share base package `com.awbd.cinema`; `@SpringBootApplication` on each service
  component-scans it, so shared `@Component`/`@Configuration` classes placed in the `common`
  module under `com.awbd.cinema.*` are auto-detected.
- The `gateway` module does **not** depend on `common` and is reactive (WebFlux); new servlet
  components must additionally be guarded with `@ConditionalOnWebApplication(type = SERVLET)` so
  they never activate in a reactive context.
- All three business services run internally on **port 8080** (`SERVER_PORT: 8080`), with
  `container_name` set and host ports published as `8081:8080`, `8082:8080`, `8083:8080` in
  `docker-compose.microservices.yml`. Both `container_name` and fixed host-port publishing block
  replicas and must be removed for the scaled services.
- All three services use servlet `context-path: /api/v1`.
- Through the gateway, the **only** public (permitAll, CSRF-exempt) routes are `/api/v1/auth/**`
  (user-service login/register). Every other routed path requires a JWT. `/internal/**` and
  `/actuator/**` are permitAll on each service but are **not** routed through the gateway.

## Components

### 1. `common` module — instance identity

New package `com.awbd.cinema.loadbalancing`. All beans annotated
`@ConditionalOnWebApplication(type = SERVLET)`.

- **`InstanceInfo`** (`@Component`): computes a stable per-instance id of the form
  `"<app-name>@<hostname>:<port>"`, where:
  - `app-name` = `${spring.application.name}`
  - `hostname` = `InetAddress.getLocalHost().getHostName()` (the container id under Docker, unique
    per replica), with fallback to the `HOSTNAME` env var then `"unknown"`.
  - `port` = `${server.port}` (8080 in containers).

  Logs `LB instance ready: <id>` at startup (via an `ApplicationReadyEvent` listener or
  `@PostConstruct`).

- **`ServedByResponseFilter`** (`OncePerRequestFilter`, `@Component`): on every response, sets
  header `X-Served-By: <instanceId>`. Additionally logs one line
  `served <method> <uri> by <instanceId>` (at INFO, under a dedicated logger so it can be filtered)
  for every request **except** `/actuator/**` (excluded to avoid noise from frequent health pings).
  This single log signal covers **both** load-balancing layers:
  - external (gateway-routed) requests appear in the target service's logs → **gateway LB**;
  - `/internal/...` requests appear in the downstream service's logs → **Feign LB**.

Auto-detected by user-service, catalog-service and booking-service via component scan. Not present
in the gateway (no `common` dependency) and inert in any reactive context due to the SERVLET guard.

**No new endpoints, controllers, whoami, Feign interceptors, or gateway routes are added.**

### 2. `docker-compose.microservices.yml` — replicas

For `user-service`, `catalog-service`, `booking-service`:

- Remove `container_name` (incompatible with replicas > 1).
- Remove host-port publishing (`8081:8080`, `8082:8080`, `8083:8080`) — fixed host ports collide
  across replicas. Services remain reachable through the gateway (`localhost:8080`).
- Add `deploy: { replicas: 2 }`.

`depends_on`, healthchecks, env, and the `mvn ... spring-boot:run` commands are otherwise
unchanged. Other services (gateway, eureka, config-server, dbs, redis, rabbitmq, client) are
untouched.

**Behavioral change (accepted):** the three business services are no longer directly reachable on
`localhost:8081/8082/8083`; they are accessed via the gateway on `localhost:8080`. This is required
for replication and is documented.

### 3. Documentation — new "Load Balancing" section in the existing root `README.md`

The README documents the commands to run and observe the demo:

**Build and run with 2 instances per service:**

```
docker compose -f docker-compose.microservices.yml up -d --build
```

(equivalently `... up -d --scale user-service=2 --scale catalog-service=2 --scale booking-service=2`).

**Confirm instances are registered:** open the Eureka dashboard at `http://localhost:8761` and
verify 2 instances per service.

**Gateway LB — quick check (no auth):** loop the public login endpoint and watch the `X-Served-By`
header alternate across user-service replicas. Documented as a small curl one-liner (bash) and an
`Invoke-WebRequest` loop (PowerShell), e.g.:

```
for i in $(seq 1 10); do \
  curl -s -o /dev/null -D - -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo"}' \
  | grep -i '^X-Served-By'; done
```

**Gateway LB + Feign LB — via logs (uniform method):** generate some traffic (use the running
client app, or authenticated requests), then grep the per-instance log lines:

```
# Gateway LB: which replica served external requests to a service
docker compose -f docker-compose.microservices.yml logs catalog-service | grep "served "

# Feign LB: which downstream replica served inter-service /internal calls
docker compose -f docker-compose.microservices.yml logs user-service catalog-service \
  | grep "served /internal"
```

**How it works (short):** Eureka registration, Spring Cloud LoadBalancer default round-robin,
`lb://` routes in the gateway, and Feign-by-service-name.

## Data flow

```
client ──POST /auth/login──▶ gateway:8080 ──(LB round-robin)──▶ user-service replica {A|B}
                                                                  └─ response header X-Served-By=user-service@<host>:8080
                                                                  └─ log: "served POST /auth/login by <id>"

client ──GET /movies (JWT)──▶ gateway:8080 ──(LB)──▶ catalog-service replica {A|B}   (log line on the replica)

(business action) ─▶ booking-service ──Feign(LB)──▶ user-service replica {A|B}   ──┐
                                     ──Feign(LB)──▶ catalog-service replica {A|B} ──┘ log: "served /internal/... by <id>"
```

## Out of scope (YAGNI)

- Custom LoadBalancer strategy / configuration (random, weighted, zone-aware).
- New demo endpoints, whoami endpoints, Feign response interceptors, header propagation.
- Per-service public gateway routes.
- Test scripts / automated integration test that boots infra and asserts distribution (deliverable
  is documented commands).
- Monitoring/Grafana dashboard changes.

## Risks / notes

- Each replica runs `mvn ... spring-boot:run`, so the first scaled startup is heavier (every
  replica builds/starts a JVM). Acceptable for a demo; document expected startup time.
- With Resilience4j fallbacks enabled, a downed downstream instance returns a fallback rather than
  an error; observation targets the happy path with all instances up.
- The `X-Served-By` header must survive the gateway hop. Spring Cloud Gateway passes through
  response headers by default; verify during implementation that the header reaches the client.
- `deploy.replicas` is honored by Docker Compose v2 `up` without Swarm; verify the installed
  Compose version honors it, otherwise fall back to documenting `--scale`.
- Per-request logging is restricted to non-`/actuator` paths to keep log volume reasonable; it uses
  a dedicated logger so the level can be tuned if needed.
```
