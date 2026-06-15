package com.awbd.cinema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesAreConfigured() {
        assertThat(routeLocator.getRoutes().collectList().block()).hasSize(3);
    }
}
