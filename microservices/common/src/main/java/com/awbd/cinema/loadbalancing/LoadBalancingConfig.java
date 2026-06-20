package com.awbd.cinema.loadbalancing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the load-balancing observability beans for servlet services (user/catalog/booking).
 *
 * <p>These are registered here as {@code @Bean}s rather than component-scanned {@code @Component}s
 * on purpose: a {@code @WebMvcTest} slice auto-includes {@link jakarta.servlet.Filter} beans but
 * not arbitrary components, so a scanned {@link ServedByResponseFilter} would be created in the
 * slice while its scanned {@link InstanceInfo} dependency would not — failing the context. Defining
 * both in one configuration guarantees they load together (full app) or not at all (slice).
 *
 * <p>The {@code SERVLET} guard keeps these off the reactive gateway's classpath/context.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoadBalancingConfig {

    @Bean
    public InstanceInfo instanceInfo(
            @Value("${spring.application.name:unknown-service}") String appName,
            @Value("${server.port:0}") String port) {
        return new InstanceInfo(appName, port);
    }

    @Bean
    public ServedByResponseFilter servedByResponseFilter(InstanceInfo instanceInfo) {
        return new ServedByResponseFilter(instanceInfo);
    }
}
