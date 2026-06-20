package com.awbd.cinema.loadbalancing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServedByResponseFilterTest {

    @Mock private InstanceInfo instanceInfo;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @Test
    void doFilterInternal_setsServedByHeaderAndContinuesChain() throws Exception {
        when(instanceInfo.getInstanceId()).thenReturn("user-service@abc123:8080");
        when(request.getRequestURI()).thenReturn("/api/v1/movies");
        when(request.getMethod()).thenReturn("GET");
        ServedByResponseFilter filter = new ServedByResponseFilter(instanceInfo);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Served-By", "user-service@abc123:8080");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldLog_isFalseForActuatorPaths_trueOtherwise() {
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/actuator/health")).isFalse();
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/movies")).isTrue();
        assertThat(ServedByResponseFilter.shouldLog("/api/v1/internal/instance")).isTrue();
    }
}
