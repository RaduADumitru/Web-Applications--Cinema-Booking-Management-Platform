package com.awbd.cinema.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingCacheErrorHandlerTest {

    private LoggingCacheErrorHandler handler;
    private Cache cache;
    private RuntimeException failure;

    @BeforeEach
    void setUp() {
        handler = new LoggingCacheErrorHandler();
        cache = new ConcurrentMapCache("test-cache");
        failure = new RuntimeException("redis down");
    }

    @Test
    void handleCacheGetError_doesNotRethrow() {
        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCachePutError_doesNotRethrow() {
        assertThatCode(() -> handler.handleCachePutError(failure, cache, "key", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCacheEvictError_doesNotRethrow() {
        assertThatCode(() -> handler.handleCacheEvictError(failure, cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCacheClearError_doesNotRethrow() {
        assertThatCode(() -> handler.handleCacheClearError(failure, cache))
                .doesNotThrowAnyException();
    }
}
