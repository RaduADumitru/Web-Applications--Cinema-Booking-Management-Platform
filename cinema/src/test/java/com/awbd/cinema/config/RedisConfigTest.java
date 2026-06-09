package com.awbd.cinema.config;

import com.awbd.cinema.utils.CacheProperties;
import com.awbd.cinema.utils.SecurityCorsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private RedisConfig redisConfig;
    private CacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        redisConfig = new RedisConfig(cacheProperties);

        // Inject private host and port primitives using ReflectionTestUtils
        ReflectionTestUtils.setField(redisConfig, "host", "localhost");
        ReflectionTestUtils.setField(redisConfig, "port", 6379);
    }

    @Test
    void cacheProperties_ShouldVerifyGettersAndSetters() {
        CacheProperties properties = new CacheProperties();
        Duration customTtl = Duration.ofMinutes(10);
        Map<String, Duration> customCaches = new HashMap<>();
        customCaches.put("movies", Duration.ofHours(1));

        properties.setDefaultTtl(customTtl);
        properties.setCaches(customCaches);

        assertThat(properties.getDefaultTtl()).isEqualTo(customTtl);
        assertThat(properties.getCaches()).containsEntry("movies", Duration.ofHours(1));
    }

    @Test
    void securityCorsProperties_ShouldVerifyGettersAndSetters() {
        SecurityCorsProperties corsProperties = new SecurityCorsProperties();
        List<String> origins = new ArrayList<>();
        origins.add("http://localhost:3000");

        corsProperties.setAllowedOrigins(origins);

        assertThat(corsProperties.getAllowedOrigins()).containsExactly("http://localhost:3000");
    }

    @Test
    void jedisConnectionFactory_ShouldConfigureFactoryCorrectly() {
        JedisConnectionFactory factory = redisConfig.jedisConnectionFactory();

        assertThat(factory).isNotNull();

        // Assert standalone configuration mappings
        RedisStandaloneConfiguration standaloneConfig = factory.getStandaloneConfiguration();
        assertThat(standaloneConfig).isNotNull();
        assertThat(standaloneConfig.getHostName()).isEqualTo("localhost");
        assertThat(standaloneConfig.getPort()).isEqualTo(6379);

        // Assert timeout parameters set in ClientConfiguration
        JedisClientConfiguration clientConfig = factory.getClientConfiguration();
        assertThat(clientConfig.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(clientConfig.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(clientConfig.isUsePooling()).isTrue();
    }

    @Test
    void redisTemplate_ShouldInitializeWithCorrectSerializers() {
        JedisConnectionFactory mockFactory = mock(JedisConnectionFactory.class);

        RedisTemplate<String, Object> template = redisConfig.redisTemplate(mockFactory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(mockFactory);

        // Verify Key and Value Serializers match your configuration setup
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }

    @Test
    void cacheManager_ShouldConfigureDefaultAndCustomTtls() {
        cacheProperties.setDefaultTtl(Duration.ofMinutes(15));

        Map<String, Duration> customMap = new HashMap<>();
        customMap.put("promoCache", Duration.ofMinutes(30));
        customMap.put("userCache", Duration.ofHours(2));
        cacheProperties.setCaches(customMap);

        JedisConnectionFactory mockFactory = mock(JedisConnectionFactory.class);

        CacheManager cacheManager = redisConfig.cacheManager(mockFactory);

        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);

        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;

        redisCacheManager.afterPropertiesSet();

        assertThat(redisCacheManager.getCacheNames())
                .containsExactlyInAnyOrder("promoCache", "userCache");
    }
}