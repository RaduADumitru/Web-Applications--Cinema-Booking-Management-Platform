package com.awbd.cinema.config;

import com.awbd.cinema.utils.CacheProperties;
import com.awbd.cinema.utils.LoggingCacheErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig(new CacheProperties());
        ReflectionTestUtils.setField(redisConfig, "host", "localhost");
        ReflectionTestUtils.setField(redisConfig, "port", 6379);
    }

    @Test
    void errorHandler_ShouldReturnLoggingCacheErrorHandler() {
        assertThat(redisConfig.errorHandler())
                .isInstanceOf(LoggingCacheErrorHandler.class);
    }
}
