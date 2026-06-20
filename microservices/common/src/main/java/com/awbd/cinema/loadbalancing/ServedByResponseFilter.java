package com.awbd.cinema.loadbalancing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with an {@code X-Served-By} header carrying this instance's id, and logs
 * one line per (non-actuator) request. The header makes gateway load balancing visible to a
 * caller; the logs make both gateway and Feign (inter-service) load balancing visible in each
 * service's container logs. Actuator paths are excluded to avoid noise from frequent health pings.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServedByResponseFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Served-By";

    private static final Logger log = LoggerFactory.getLogger(ServedByResponseFilter.class);

    private final InstanceInfo instanceInfo;

    public ServedByResponseFilter(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HEADER, instanceInfo.getInstanceId());
        if (shouldLog(request.getRequestURI())) {
            log.info("served {} {} by {}", request.getMethod(), request.getRequestURI(),
                    instanceInfo.getInstanceId());
        }
        filterChain.doFilter(request, response);
    }

    static boolean shouldLog(String requestUri) {
        return requestUri == null || !requestUri.contains("/actuator/");
    }
}
