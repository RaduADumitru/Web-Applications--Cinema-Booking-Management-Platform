package com.awbd.cinema.config;

import com.awbd.cinema.utils.JwtUtil;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenInterceptorTest {

    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private ServiceTokenInterceptor interceptor;

    @Test
    void apply_ShouldAddBearerAuthorizationHeaderWithMintedServiceToken() {
        ReflectionTestUtils.setField(interceptor, "applicationName", "booking-service");
        when(jwtUtil.generateServiceToken("booking-service")).thenReturn("minted_token");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer minted_token");
    }
}
