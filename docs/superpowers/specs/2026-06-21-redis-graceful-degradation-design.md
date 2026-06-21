# Redis Cache Graceful Degradation — Design

## Problem

`catalog-service` and `booking-service` enable Spring Cache backed by Redis
(`RedisConfig`, `@EnableCaching`, `@Cacheable`/`@CacheEvict`/`@CachePut`). Neither
service registers a `CacheErrorHandler`, so when Redis is unreachable at runtime,
cache operations propagate the connection exception and turn read/write endpoints
into HTTP 500s instead of falling back to the database.

The gateway uses Redis only for rate limiting (`RedisRateLimiter`), which already
fails open when Redis is down, so it is out of scope.

## Goal

When Redis is unreachable, cache operations become best-effort:

- A failed cache **get** falls through to the underlying method (the database).
- A failed cache **put / evict / clear** is logged and ignored (the business
  method has already executed).

No endpoint should 500 solely because Redis is down.

## Design

### 1. Shared `LoggingCacheErrorHandler` in `common`

A plain class in the `common` module (package `com.awbd.cinema.utils`, alongside
`CacheProperties`) implementing `org.springframework.cache.interceptor.CacheErrorHandler`.
It overrides all four hooks to log a `WARN` (cache name, key, cause) and swallow
the exception:

- `handleCacheGetError`
- `handleCachePutError`
- `handleCacheEvictError`
- `handleCacheClearError`

It is a plain class, **not** a `@Component`, so it is not component-scanned in
`common`. This avoids the known pitfall where shared beans declared in `common`
break service `@WebMvcTest` slices (see memory: common-web-beans-need-configuration-wiring).
Each service instantiates it explicitly.

### 2. Wire per-service

Both `RedisConfig` classes (`catalog-service`, `booking-service`) additionally
`implements CachingConfigurer` and override:

```java
@Override
public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
}
```

`@EnableCaching` auto-detects the `errorHandler()` exposed by a `CachingConfigurer`
configuration bean — no other wiring required. The existing `@Profile("!test")`
guard is retained, so test slices are unaffected.

### 3. Tests (test-first)

- **`LoggingCacheErrorHandlerTest`** (common): each of the four handler methods does
  not rethrow when given a `RuntimeException`.
- **`RedisConfigTest`**: assert `redisConfig.errorHandler()` returns a non-null
  `LoggingCacheErrorHandler`. catalog-service already has this test (extend it);
  booking-service has none, so a minimal one is added for parity.

## Update (2026-06-21): gateway health & rate-limiter degradation

Manual testing (stop Redis on the running Docker stack) showed the cache-layer fix
was necessary but not sufficient: the app still went down, with the failure surfacing
in the **gateway**. The original "gateway already fails open, out of scope" assumption
was wrong. Two non-degrading gateway dependencies on Redis:

1. **Redis health indicator gates liveness.** `DataRedisReactiveHealthIndicator` is
   folded into `/actuator/health`. Redis down -> the indicator's command times out
   (Lettuce default 60s) -> health reports DOWN (503). The gateway's Docker healthcheck
   (`curl -f .../actuator/health`) then fails and the container is marked unhealthy.
   The `client` container is gated by `depends_on: gateway: condition: service_healthy`,
   so on any restart/recreate with Redis down it never starts -> browser gets connection
   refused. catalog/booking hit the same indicator (imperative Jedis variant) and also go
   Docker-unhealthy (routing survives only because `eureka.client.healthcheck.enabled` is off).

2. **Rate limiter stalls every request.** Every route applies `requestRateLimiter`
   backed by `RedisRateLimiter` (reactive Lettuce). With Redis down each `isAllowed`
   waits the 60s command timeout before failing open -> effective outage.

### Fix

- **`config-repo/application.yml`** (shared): `management.health.redis.enabled: false`.
  Redis is non-critical everywhere here (cache + rate limit), so its outage must not
  flip `/actuator/health`. Keeps all services Docker-healthy and lets the gateway-gated
  `client` start.
- **`config-repo/gateway.yml`**: `spring.data.redis.timeout: 250ms` +
  `connect-timeout: 250ms`, so the limiter fails open in ~250ms instead of 60s.
  catalog/booking are unaffected (Jedis, own 2s timeouts + `CacheErrorHandler`).

## Out of scope

- Startup resilience when Redis is unreachable at boot (no longer a hard failure once
  the health indicator is excluded).
- catalog/booking connect/read timeout tuning (currently 2s; acceptable with the
  `CacheErrorHandler` fallthrough).
- Reducing Lettuce reconnect-attempt log noise while Redis is down (cosmetic).

## Affected files

- `microservices/common/src/main/java/com/awbd/cinema/utils/LoggingCacheErrorHandler.java` (new)
- `microservices/common/src/test/java/com/awbd/cinema/utils/LoggingCacheErrorHandlerTest.java` (new)
- `microservices/catalog-service/.../config/RedisConfig.java` (implement `CachingConfigurer`)
- `microservices/booking-service/.../config/RedisConfig.java` (implement `CachingConfigurer`)
- `microservices/catalog-service/.../config/RedisConfigTest.java` (extend: assert error handler)
- `microservices/booking-service/.../config/RedisConfigTest.java` (new: assert error handler)
