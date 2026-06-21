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

## Out of scope

- Gateway rate limiter (already fails open).
- Startup resilience when Redis is unreachable at boot.
- Connect/read timeout tuning (currently 2s).

## Affected files

- `microservices/common/src/main/java/com/awbd/cinema/utils/LoggingCacheErrorHandler.java` (new)
- `microservices/common/src/test/java/com/awbd/cinema/utils/LoggingCacheErrorHandlerTest.java` (new)
- `microservices/catalog-service/.../config/RedisConfig.java` (implement `CachingConfigurer`)
- `microservices/booking-service/.../config/RedisConfig.java` (implement `CachingConfigurer`)
- `microservices/catalog-service/.../config/RedisConfigTest.java` (extend: assert error handler)
- `microservices/booking-service/.../config/RedisConfigTest.java` (new: assert error handler)
