package com.awbd.cinema.loadbalancing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceInfoTest {

    @Test
    void buildInstanceId_composesAppNameHostAndPort() {
        String id = InstanceInfo.buildInstanceId("user-service", "abc123", "8080");

        assertThat(id).isEqualTo("user-service@abc123:8080");
    }

    @Test
    void resolveHostname_neverReturnsNullOrBlank() {
        assertThat(InstanceInfo.resolveHostname()).isNotBlank();
    }
}
