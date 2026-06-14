package com.awbd.cinema.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    private Duration defaultTtl = Duration.ofMinutes(5);
    private Map<String, Duration> caches = new HashMap<>();
}
