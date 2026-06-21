package com.awbd.cinema.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Makes Spring cache operations best-effort so the application degrades gracefully
 * when the cache backend (Redis) is unreachable. Every cache failure is logged at
 * WARN and swallowed:
 *
 * <ul>
 *   <li>a failed {@code get} lets Spring fall through to the underlying method (the database);</li>
 *   <li>a failed {@code put}/{@code evict}/{@code clear} is ignored — the business method has already run.</li>
 * </ul>
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET failed [cache={}, key={}] - falling back to source: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT failed [cache={}, key={}] - value not cached: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT failed [cache={}, key={}] - entry not evicted: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR failed [cache={}] - cache not cleared: {}",
                cache.getName(), exception.getMessage());
    }
}
