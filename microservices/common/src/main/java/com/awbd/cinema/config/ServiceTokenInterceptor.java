package com.awbd.cinema.config;

import com.awbd.cinema.utils.JwtUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTokenInterceptor implements RequestInterceptor {

    private final JwtUtil jwtUtil;

    @Value("${spring.application.name}")
    private String applicationName;

    @Override
    public void apply(RequestTemplate template) {
        String token = jwtUtil.generateServiceToken(applicationName);
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        log.info("Attached service token as '{}' for outgoing {} {}",
                applicationName, template.method(), template.path());
    }
}
