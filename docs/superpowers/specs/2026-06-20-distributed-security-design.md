# Distributed Security — Service-to-Service JWT — Design

## 1. Goal

Implement distributed security between the microservices (coursework
requirement #6). Concretely: give each service its **own verifiable identity**
so that the internal service-to-service endpoints (`/internal/**`) are
authenticated rather than open.

Direction chosen (over OAuth2/Keycloak): a **lightweight service JWT** that
extends the existing shared `JwtUtil` infrastructure in `common`. No new
infrastructure container, smallest change that closes the gap.

HTTPS is **explicitly deferred** to a future phase (see §9).

## 2. Context — what exists today

The reactor (`microservices/`, Spring Boot 4.0.7 / Spring Cloud 2025.1.2 /
Java 21) already has working **user-context** distributed security:

- At login, `user-service` issues a claims-based JWT (`typ=ACCESS`, `userId`,
  `role`) in an httpOnly `jwt` cookie, signed with the shared
  `jwt.secret.key`.
- A stateless `JwtAuthenticationFilter` (in `common`) validates that cookie on
  every service and rebuilds the authentication from claims — no DB lookup.
- `FeignAuthInterceptor` (in `common`, a global `@Component`
  `RequestInterceptor`) forwards the incoming `Cookie` header on outgoing Feign
  calls, so user-initiated cross-service calls carry the user's JWT.

The **gap**: the three internal Feign endpoints are `permitAll` in every
service's `SecurityConfig` — protected only by Docker-network isolation and by
not being routed through the gateway. The original extraction spec
(`2026-06-14-microservices-extraction-design.md` §6.4) flagged this as "the
candidate for service-to-service auth hardening in a future phase." This spec
is that phase.

The three internal contracts:

| # | Caller          | Callee          | Endpoint                                            | User JWT present? |
|---|-----------------|-----------------|-----------------------------------------------------|-------------------|
| ① | booking-service | catalog-service | `GET/POST /internal/ticket-setup[/bulk]`            | yes (admin req)   |
| ② | booking-service | user-service    | `GET/PATCH /internal/users/{id}/loyalty-points`     | yes (user req)    |
| ③ | user-service    | booking-service | `POST /internal/notifications`                      | **no** (register) |

Contract ③ fires during registration, **before any user JWT exists** — which
is exactly why `/internal/**` was made `permitAll`. Any service-to-service
auth scheme must work without a user present.

None of the three callees authorize on the *user's* identity: ① takes
`seatId/roomId/sessionId`, ② takes `{id}` in the path, ③ takes `userId` in the
body. User-level authorization already happened at the public entry point.
This is why the forwarded user cookie was never actually authorizing
`/internal/**`, and why a pure service credential is sufficient.

## 3. Token model

A short-lived JWT minted by the **calling** service for each outgoing Feign
call.

- **Claims:** `typ=SERVICE`, `sub=<spring.application.name>` (e.g.
  `booking-service`), `iat`, `exp`.
- **TTL:** ~60 seconds (tunable, see §6). Minted fresh per call; short expiry
  caps replay if a token leaks.
- **Signing:** HMAC with the **existing shared `jwt.secret.key`** — no new
  secret, no new container.
- **Transport:** `Authorization: Bearer <service-jwt>` on the outgoing
  request, kept separate from the user's `jwt` cookie.

**Why the shared key is safe here:** the `typ` claim makes the two token kinds
non-interchangeable. The `/internal/**` chain accepts only `typ=SERVICE`; the
public chain accepts only the `typ=ACCESS` cookie. A user `ACCESS` token
replayed at `/internal/**` is rejected (wrong `typ`), and a `SERVICE` token
presented on a public path is ignored (the public filter only reads the
cookie). Forging either token requires the server-only secret, which never
reaches a browser.

## 4. Components

### 4.1 `common` — `JwtUtil` additions

- `generateServiceToken(String serviceName)` — builds the `typ=SERVICE` token
  per §3, signed with the existing signing key.
- `validateServiceToken(String token)` — verifies signature + expiry +
  `typ=SERVICE`; returns the `sub` (calling service name) on success, throws /
  signals invalid otherwise.

Existing user-token methods (`generateToken`, `generateRefreshToken`,
`extractRole`, `isTokenValid`, …) are untouched.

### 4.2 `common` — `ServiceTokenInterceptor` (replaces `FeignAuthInterceptor`)

A global Feign `RequestInterceptor` (`@Component` in
`com.awbd.cinema.config`, auto-registered for all clients via the shared base
package). For every outgoing Feign call it mints a fresh service token
(`jwtUtil.generateServiceToken(applicationName)`) and sets
`Authorization: Bearer …`.

`FeignAuthInterceptor` (cookie forwarding) is **deleted** — the internal
endpoints take their inputs explicitly and no longer need the user cookie.
Its test is removed/replaced accordingly.

> Consequence: a *future* internal endpoint that needs user context must pass
> `userId` explicitly (as all three current contracts already do). Documented
> as an accepted trade-off.

### 4.3 `common` — `ServiceTokenAuthenticationFilter`

A plain `OncePerRequestFilter` (NOT a `@Component`). It is instantiated
directly inside each service's `SecurityConfig` as
`new ServiceTokenAuthenticationFilter(jwtUtil)` — mirroring how
`CsrfCookieFilter` is wired today, and deliberately avoiding the
`common`-web-bean pitfall recorded in project memory (shared `@Component`
servlet Filters in `common` break service `@WebMvcTest` slices).

Behaviour:
- Reads the `Authorization: Bearer` header.
- Validates via `jwtUtil.validateServiceToken(...)`.
- On success: sets a `UsernamePasswordAuthenticationToken` whose principal is
  the `sub` service name and whose single authority is a
  `SimpleGrantedAuthority("ROLE_SERVICE")`.
- On failure/absence: sets no authentication and continues the chain (the
  authorization rule then yields 401/403).

`ROLE_SERVICE` is set as a literal authority. It is **not** added to the
`Role` enum, which stays user-only (`ROLE_OWNER/STAFF/USER`).

## 5. Per-service security wiring

Applies identically to `user-service`, `catalog-service`, `booking-service`.
Two ordered `SecurityFilterChain` beans:

**Chain 1 — `@Order(1)`, `securityMatcher("/internal/**")`:**
- CSRF disabled, stateless.
- Adds the `ServiceTokenAuthenticationFilter`.
- `authorizeHttpRequests`: `anyRequest().hasRole("SERVICE")`.

**Chain 2 — `@Order(2)`, catch-all (the existing chain):**
- Unchanged user-JWT behaviour (`JwtAuthenticationFilter`, CSRF cookie, CORS
  disabled at service level, OPTIONS permit, `/actuator/**` permit, the
  per-service public matchers like `/auth/**`).
- **Removes** the now-obsolete `/internal/**` entries: the
  `.requestMatchers("/internal/**").permitAll()` line and the
  `/internal/**` token in `csrf().ignoringRequestMatchers(...)`. Those paths
  are now owned exclusively by Chain 1.

Net effect: internal endpoints accept **only** valid service tokens; public
endpoints accept **only** the user cookie. `/actuator/**` remains open for the
container healthchecks.

## 6. Configuration

- **No new environment variables.** Service tokens reuse `jwt.secret.key`,
  already shared by every service.
- Optional tunable `service.token.ttl-seconds` (default `60`) — lives in the
  shared config (`application.yml` on the config server), read by `JwtUtil`.
- `spring.application.name` is already set per service and is reused as the
  token `sub`.

## 7. The three flows after the change

- **① booking→catalog (ticket-setup)** / **② booking↔user (loyalty-points)** —
  the caller mints a service token instead of riding on the forwarded user
  cookie. User-level authorization is unchanged: it still happens at the
  public controller the user hit, before the Feign call.
- **③ user→booking (registration notification)** — now works with a real
  credential. Minting needs only the shared secret, not a user, so the
  pre-JWT registration path authenticates properly instead of depending on
  `permitAll`. This closes the flagged gap.

## 7a. Observability / demo

Each internal call emits a matched pair of `INFO` log lines (the default level
for `com.awbd.cinema`), giving positive, demoable proof that the scheme works
end to end:

- **Caller** (`ServiceTokenInterceptor`): `Attached service token as
  '<service>' for outgoing <METHOD> <path>`.
- **Callee** (`ServiceTokenAuthenticationFilter`, success): `Internal request
  authenticated as service '<service>' -> <METHOD> <uri>`.

A single user action (e.g. registration → user-service → booking-service)
produces both lines across two services' logs. The negative path is equally
visible: a direct call to an internal endpoint with a missing/forged token
returns `401` and, for a malformed token, logs `Rejecting internal request
with invalid service token: …`. The README documents the exact commands.

## 8. Testing

- **Unit — `JwtUtil`:** `generateServiceToken`/`validateServiceToken` round
  trip; rejection of expired token, wrong `typ` (an `ACCESS` token), and bad
  signature.
- **Unit — `ServiceTokenAuthenticationFilter`:** sets `ROLE_SERVICE` auth for
  a valid token; leaves the context empty for missing/invalid tokens.
- **Slice (per service):** an `/internal/**` endpoint returns **401/403
  without** a service token and **200 with** a valid one.
- **Updated existing tests:** any test that currently calls `/internal/**`
  relying on `permitAll` is updated to send a valid service token. The three
  Feign fallback-factory tests are client-side and unaffected.
- **Gate:** full reactor `mvn clean verify` green before pushing (per the
  project-memory lesson about `common` web beans + `@WebMvcTest` slices).

## 9. Out of scope / future hardening

- **HTTPS / TLS** — deferred. The easiest future increment is edge HTTPS
  (self-signed cert on the gateway, `https://localhost:8443`); it needs no
  frontend code change (the client reads `API_URL` at container start), only a
  different injected value for the microservices compose. mTLS between
  services is the heavier, lower-value option for this PoC.
- **Per-caller allowlisting** — restricting *which* service may call *which*
  internal endpoint (beyond "any valid service token is trusted"). The `sub`
  claim already carries the caller identity, so this is a small future
  addition.
- **Separate service-token signing key** — isolating service tokens from the
  user-token secret. Not needed while the `typ` claim keeps them
  non-interchangeable.
- **OAuth2/Keycloak** — the heavier identity-provider direction, not taken.
