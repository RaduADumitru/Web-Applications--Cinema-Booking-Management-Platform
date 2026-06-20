package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=0",
        "spring.profiles.active=native",
        "spring.cloud.config.server.native.search-locations=classpath:/test-config"
})
class ConfigServerApplicationTest {

    @Test
    void contextLoads() {
    }
}
