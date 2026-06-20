# Client-Side Load Balancing Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the platform's existing Spring Cloud LoadBalancer setup runnable with 2 instances per business service and observable/testable via an instance-id response header, per-instance logging, and documented commands.

**Architecture:** Add two small servlet components to the shared `common` module (an `InstanceInfo` bean computing a unique per-instance id, and a `ServedByResponseFilter` that stamps an `X-Served-By` header and logs every non-actuator request). Scale the three business services to 2 replicas in `docker-compose.microservices.yml`. Document the run/observe commands in the root `README.md`. No new endpoints, no LB-strategy changes (default round-robin kept).

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Cloud 2025.1.2, Spring Cloud LoadBalancer (already present transitively), Maven (multi-module), Docker Compose v2, JUnit 5 + Mockito + AssertJ (via `spring-boot-starter-test`).

**Spec:** `docs/superpowers/specs/2026-06-20-client-side-load-balancing-design.md`

---

## File Structure

| Path                                                                                               | Responsibility                                                                | Action |
|----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|--------|
| `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/InstanceInfo.java`               | Compute + expose a stable per-instance id; log it at startup                  | Create |
| `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/ServedByResponseFilter.java`     | Set `X-Served-By` header + log each non-actuator request                      | Create |
| `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/InstanceInfoTest.java`           | Unit test for id composition                                                  | Create |
| `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/ServedByResponseFilterTest.java` | Unit test for header + log-exclusion logic                                    | Create |
| `docker-compose.microservices.yml`                                                                 | Run 2 replicas of each business service (no fixed container name / host port) | Modify |
| `README.md`                                                                                        | Document run + observe commands; fix the Ports table                          | Modify |

**Conventions to follow (verified in the repo):**
- All services share base package `com.awbd.cinema`, so `@Component` classes under
  `com.awbd.cinema.loadbalancing` in `common` are auto-detected by user-service, catalog-service,
  and booking-service. The `gateway` module does **not** depend on `common`.
- New servlet beans are guarded with `@ConditionalOnWebApplication(type = SERVLET)` (defensive).
- Existing `common` tests (`com.awbd.cinema.security.JwtAuthenticationFilterTest`) use
  `@ExtendWith(MockitoExtension.class)`, `@Mock`, AssertJ `assertThat`, and Mockito `verify` —
  match that style.
- Maven commands run from the `microservices/` directory (no wrapper present; use `mvn`).

---

### Task 1: `InstanceInfo` bean (common)

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/InstanceInfo.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/InstanceInfoTest.java`

- [ ] **Step 1: Write the failing test**

Create `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/InstanceInfoTest.java`:

```java
package com.awbd.cinema.loadbalancing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceInfoTest {

    @Test
    void buildInstanceId_composesAppNameHostAndPort() {
        String id = InstanceInfo.buildInstanceId("user-service", "abc123", "8080");

        assertThat(id).isEqualTo("user-service@abc123:8080");
    }

    @Test
    void resolveHostname_neverReturnsNullOrBlank() {
        assertThat(InstanceInfo.resolveHostname()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `microservices/`): `mvn -pl common -Dtest=InstanceInfoTest test`
Expected: BUILD FAILURE — compilation error `cannot find symbol ... InstanceInfo`.

- [ ] **Step 3: Write the minimal implementation**

Create `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/InstanceInfo.java`:

```java
package com.awbd.cinema.loadbalancing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Holds a stable, human-readable identifier for this running service instance, of the form
 * {@code <app-name>@<hostname>:<port>}. Under Docker the hostname is the container id, so each
 * replica gets a distinct id — which is what makes load balancing across instances observable.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InstanceInfo {

    private static final Logger log = LoggerFactory.getLogger(InstanceInfo.class);

    private final String instanceId;

    public InstanceInfo(
            @Value("${spring.application.name:unknown-service}") String appName,
            @Value("${server.port:0}") String port) {
        this.instanceId = buildInstanceId(appName, resolveHostname(), port);
        log.info("LB instance ready: {}", instanceId);
    }

    static String buildInstanceId(String appName, String hostname, String port) {
        return appName + "@" + hostname + ":" + port;
    }

    static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String envHost = System.getenv("HOSTNAME");
            return (envHost != null && !envHost.isBlank()) ? envHost : "unknown";
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run (from `microservices/`): `mvn -pl common -Dtest=InstanceInfoTest test`
Expected: BUILD SUCCESS, 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/loadbalancing/InstanceInfo.java \
        microservices/common/src/test/java/com/awbd/cinema/loadbalancing/InstanceInfoTest.java
git commit -m "Add InstanceInfo for per-instance id (load balancing demo)"
```

---

### Task 2: `ServedByResponseFilter` (common)

**Files:**
- Create: `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/ServedByResponseFilter.java`
- Test: `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/ServedByResponseFilterTest.java`

- [ ] **Step 1: Write the failing test**

Create `microservices/common/src/test/java/com/awbd/cinema/loadbalancing/ServedByResponseFilterTest.java`:

```java
package com.awbd.cinema.loadbalancing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServedByResponseFilterTest {

    @Mock private InstanceInfo instanceInfo;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @Test
    void doFilterInternal_setsServedByHeaderAndContinuesChain() throws Exception {
        when(instanceInfo.getInstanceId()).thenReturn("user-service@abc123:8080");
        when(request.getRequestURI()).thenReturn("/api/v1/movies");
        when(request.getMethod()).thenReturn("GET");
        ServedByResponseFilter filter = new ServedByResponseFilter(instanceInfo);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Served-By", "user-service@abc123:8080");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldLog_isFalseForActuatorPaths_trueOtherwise() {
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/actuator/health")).isFalse();
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/movies")).isTrue();
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/internal/instance")).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `microservices/`): `mvn -pl common -Dtest=ServedByResponseFilterTest test`
Expected: BUILD FAILURE — compilation error `cannot find symbol ... ServedByResponseFilter`.

- [ ] **Step 3: Write the minimal implementation**

Create `microservices/common/src/main/java/com/awbd/cinema/loadbalancing/ServedByResponseFilter.java`:

```java
package com.awbd.cinema.loadbalancing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with an {@code X-Served-By} header carrying this instance's id, and logs
 * one line per (non-actuator) request. The header makes gateway load balancing visible to a
 * caller; the logs make both gateway and Feign (inter-service) load balancing visible in each
 * service's container logs. Actuator paths are excluded to avoid noise from frequent health pings.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServedByResponseFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Served-By";

    private static final Logger log = LoggerFactory.getLogger(ServedByResponseFilter.class);

    private final InstanceInfo instanceInfo;

    public ServedByResponseFilter(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HEADER, instanceInfo.getInstanceId());
        if (shouldLog(request.getRequestURI())) {
            log.info("served {} {} by {}", request.getMethod(), request.getRequestURI(),
                    instanceInfo.getInstanceId());
        }
        filterChain.doFilter(request, response);
    }

    static boolean shouldLog(String requestUri) {
        return requestUri == null || !requestUri.contains("/actuator/");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run (from `microservices/`): `mvn -pl common -Dtest=ServedByResponseFilterTest test`
Expected: BUILD SUCCESS, 2 tests pass.

- [ ] **Step 5: Run the whole common module test suite (no regressions)**

Run (from `microservices/`): `mvn -pl common test`
Expected: BUILD SUCCESS — the new tests plus the existing `JwtAuthenticationFilterTest` /
`JwtUtilTest` all pass.

- [ ] **Step 6: Commit**

```bash
git add microservices/common/src/main/java/com/awbd/cinema/loadbalancing/ServedByResponseFilter.java \
        microservices/common/src/test/java/com/awbd/cinema/loadbalancing/ServedByResponseFilterTest.java
git commit -m "Add X-Served-By response filter with per-request instance logging"
```

---

### Task 3: Scale business services to 2 replicas in Docker Compose

**Files:**
- Modify: `docker-compose.microservices.yml`

Each of the three business services currently sets a fixed `container_name` and publishes a host
port (`8081/8082/8083`). Both block replicas (name collision; host-port collision). Remove them and
add `deploy: replicas: 2`. The services stay reachable through the gateway (`localhost:8080`); their
internal port stays `8080` and the healthcheck is unchanged.

- [ ] **Step 1: Edit `user-service`**

In `docker-compose.microservices.yml`, find the `user-service:` block. Remove the
`container_name: ms-user-service` line, remove the two `ports:` lines
(`    ports:` and `      - "8081:8080"`), and add a `deploy:` block. Result:

```yaml
  user-service:
    <<: *ms-common
    command: sh -c "mvn -o -pl user-service spring-boot:run"
    deploy:
      replicas: 2
    environment:
      SERVER_PORT: 8080
      DATABASE_URL: jdbc:postgresql://user-db:5432/${USER_DB_NAME}
      EUREKA_SERVER_URL: http://discovery-server:8761/eureka/
      SECURITY_CORS_ALLOWED_ORIGINS: http://localhost:4200
      CONFIG_SERVER_URL: http://config-server:8888
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      user-db:
        condition: service_healthy
      discovery-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/v1/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 180s
    networks:
      - microservices-network
```

- [ ] **Step 2: Edit `catalog-service`**

In the `catalog-service:` block: remove `container_name: ms-catalog-service`, remove the `ports:`
block (`    ports:` and `      - "8082:8080"`), and add `deploy:\n      replicas: 2` immediately
after the `command:` line (same shape as user-service). Leave `environment`, `depends_on`,
`healthcheck`, and `networks` unchanged.

- [ ] **Step 3: Edit `booking-service`**

In the `booking-service:` block: remove `container_name: ms-booking-service`, remove the `ports:`
block (`    ports:` and `      - "8083:8080"`), and add `deploy:\n      replicas: 2` immediately
after the `command:` line. Leave `environment`, `depends_on`, `healthcheck`, and `networks`
unchanged.

- [ ] **Step 4: Validate the compose file parses and reflects the changes**

Run: `docker compose -f docker-compose.microservices.yml config`
Expected: prints the fully-resolved config with no error. Confirm in the output that
`user-service`, `catalog-service`, and `booking-service` each show `replicas: 2`, have **no**
`container_name`, and have **no** `ports:` mapping.

If `docker compose` is unavailable in the execution environment, instead verify by inspection:
`grep -n "container_name\|replicas\|8081:8080\|8082:8080\|8083:8080" docker-compose.microservices.yml`
Expected: three `replicas: 2` lines; the three business-service `container_name` lines and the
`808x:8080` mappings are gone (the db/redis/etc. `container_name` lines remain).

- [ ] **Step 5: Commit**

```bash
git add docker-compose.microservices.yml
git commit -m "Run 2 replicas per business service for load balancing demo"
```

---

### Task 4: Document the demo in `README.md`

**Files:**
- Modify: `README.md` (the `## Ports` table, and a new `## Load Balancing (multiple instances)` section before `## Stop`)

- [ ] **Step 1: Update the `## Ports` table**

The three business services no longer publish direct host ports. In `README.md`, delete these three
table rows:

```
| http://localhost:8081/api/v1 | user-service (direct, debugging) |
| http://localhost:8082/api/v1 | catalog-service (direct) |
| http://localhost:8083/api/v1 | booking-service (direct) |
```

Then, immediately below the Ports table (after the existing `/internal/**` note paragraph), add:

```
The three business services (`user-service`, `catalog-service`, `booking-service`) run **2
instances each** and are reachable only through the gateway on `http://localhost:8080` — they no
longer publish direct host ports. See **Load Balancing** below.
```

- [ ] **Step 2: Add the Load Balancing section**

Insert the following section immediately before the `## Stop` heading in `README.md`:

````markdown
## Load Balancing (multiple instances)

The stack runs **2 instances of each business service** (`user-service`, `catalog-service`,
`booking-service`) via Docker Compose `deploy.replicas`. Client-side load balancing is provided by
**Spring Cloud LoadBalancer** (default round-robin) in two places:

- the **gateway** routes (`lb://user-service`, …) distribute external requests across instances;
- **Feign** clients distribute inter-service (`/internal/**`) calls across instances by service name.

Each instance stamps an `X-Served-By: <service>@<host>:<port>` response header and logs
`served <method> <uri> by <id>` for every non-actuator request.

### Run with 2 instances per service

```bash
docker compose -f docker-compose.microservices.yml up -d --build
```

(equivalently: `... up -d --build --scale user-service=2 --scale catalog-service=2 --scale booking-service=2`)

The first build is slower because every replica builds and starts its own JVM.

### Confirm both instances registered

Open the Eureka dashboard at http://localhost:8761 — each service should list **2** instances.

### See gateway load balancing (no auth needed)

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

### See gateway + Feign load balancing (logs)

Run the register/login smoke test a few times. Registration also triggers the user→booking Feign
**welcome notification**, exercising inter-service load balancing. Then read the per-instance log
lines:

```bash
# Gateway LB: which user-service instance served the external login requests
docker compose -f docker-compose.microservices.yml logs user-service | grep "served "

# Feign LB: which booking-service instance served the inter-service /internal notification calls
docker compose -f docker-compose.microservices.yml logs booking-service | grep "served /internal"
```

Across requests the instance id (`<service>@<host>:<port>`) varies, showing round-robin
distribution.

### How it works

Each instance registers with Eureka (`prefer-ip-address: true`, so replicas get distinct ids).
The gateway resolves `lb://<service>` routes and Feign resolves `@FeignClient(name = "<service>")`
through Eureka, and **Spring Cloud LoadBalancer** picks an instance per call using its default
round-robin strategy. No load-balancer configuration is added — multiplicity plus the
`X-Served-By` header and request logging are all that's needed to demonstrate it.
````

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Document load balancing demo (run + observe commands)"
```

---

### Task 5: End-to-end verification (Docker)

This task validates the whole demo against a running stack. It requires Docker. No code changes /
no commit — if any check fails, fix the relevant earlier task.

**Files:** none (verification only)

- [ ] **Step 1: Build and start with replicas**

Run: `docker compose -f docker-compose.microservices.yml up -d --build`
Expected: all containers start; `docker compose -f docker-compose.microservices.yml ps` shows
**two** containers each for user-service, catalog-service, booking-service (names suffixed `-1` /
`-2`), all eventually `healthy`. (Allow several minutes — each replica builds/starts a JVM.)

- [ ] **Step 2: Confirm Eureka shows 2 instances per service**

Open http://localhost:8761 and confirm USER-SERVICE, CATALOG-SERVICE, and BOOKING-SERVICE each list
2 instances. If a service shows only 1, the replicas registered with the same id — add
`eureka.instance.instance-id: ${spring.application.name}:${random.uuid}` under `eureka.instance` in
`microservices/config-server/config-repo/application.yml`, then rebuild. (Not expected, since
`prefer-ip-address: true` already yields distinct ids per container ip.)

- [ ] **Step 3: Register the demo user**

Run:
```bash
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Password123!","confirmPassword":"Password123!","email":"demo@example.com","firstName":"Demo","lastName":"User","phoneNumber":"+1234567890"}'
```
Expected: `201`. (On a re-run, an "already exists" error is fine.)

- [ ] **Step 4: Verify gateway LB via the header loop**

Run the bash login loop from Task 4 / Step 2.
Expected: `X-Served-By` shows **two distinct** `user-service@<host>:8080` ids across the 10 lines.

- [ ] **Step 5: Verify Feign LB via logs**

Run:
```bash
docker compose -f docker-compose.microservices.yml logs booking-service | grep "served /internal"
```
Expected: lines like `served POST /api/v1/internal/notifications by booking-service@<host>:8080`
with **more than one** distinct booking-service id across repeated registrations.

- [ ] **Step 6: Confirm actuator noise is excluded**

Run:
```bash
docker compose -f docker-compose.microservices.yml logs user-service | grep "served /api/v1/actuator" | head
```
Expected: **no** output (health-check pings are not logged by the filter).

- [ ] **Step 7: Tear down (optional)**

Run: `docker compose -f docker-compose.microservices.yml down`

---

## Notes for the implementer

- Maven commands run from the `microservices/` directory. `mvn -pl common test` works standalone
  because `common` has no intra-project dependencies.
- After changing `common`, the `--build` flag in Task 5 is required: the `microservices/Dockerfile`
  runs `mvn -Dmaven.test.skip=true install` to bake `common` (and the services) into each image's
  local Maven repo, so a rebuild is what propagates the new `common` classes to the running
  services.
- Do **not** add the filter/`InstanceInfo` to the `gateway` module — it is reactive (WebFlux) and
  does not depend on `common`; the `@ConditionalOnWebApplication(type = SERVLET)` guard is a
  belt-and-suspenders safeguard, not a reason to wire it in.
- The gateway's Redis rate limiter defaults to 50 req/s replenish / 100 burst, so the 10-request
  demo loop will not be throttled.
