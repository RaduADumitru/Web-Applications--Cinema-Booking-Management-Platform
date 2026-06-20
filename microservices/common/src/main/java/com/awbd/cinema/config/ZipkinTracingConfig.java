package com.awbd.cinema.config;

import brave.Tracing;
import brave.sampler.Sampler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@Configuration
public class ZipkinTracingConfig {

    @Value("${management.zipkin.tracing.endpoint:http://zipkin:9411/api/v2/spans}")
    private String zipkinEndpoint;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Bean(destroyMethod = "close")
    public URLConnectionSender zipkinSender() {
        return URLConnectionSender.create(zipkinEndpoint);
    }

    @Bean(destroyMethod = "close")
    public AsyncZipkinSpanHandler zipkinSpanHandler(URLConnectionSender sender) {
        return AsyncZipkinSpanHandler.create(sender);
    }

    @Bean(destroyMethod = "close")
    public Tracing braveTracing(AsyncZipkinSpanHandler spanHandler) {
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(spanHandler)
                .build();
    }

    @Bean
    public brave.Tracer braveTracer(Tracing tracing) {
        return tracing.tracer();
    }

    @Bean
    public BraveCurrentTraceContext braveCurrentTraceContext(Tracing tracing) {
        return new BraveCurrentTraceContext(tracing.currentTraceContext());
    }

    @Bean
    public BraveTracer micrometerBraveTracer(brave.Tracer braveTracer, BraveCurrentTraceContext currentTraceContext) {
        return new BraveTracer(braveTracer, currentTraceContext, new BraveBaggageManager());
    }

    @Bean
    public BravePropagator bravePropagator(Tracing tracing) {
        return new BravePropagator(tracing);
    }

    @Bean
    public ObservationRegistry observationRegistry(BraveTracer tracer, BravePropagator propagator) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer))
                .observationHandler(new PropagatingReceiverTracingObservationHandler<>(tracer, propagator))
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(tracer, propagator));
        return registry;
    }
}
