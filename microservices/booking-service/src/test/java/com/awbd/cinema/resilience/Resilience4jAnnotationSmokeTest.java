package com.awbd.cinema.resilience;

import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the Resilience4j @Retry annotation aspect is active under Spring Boot 4
 * (the one compatibility caveat: the aspects ship in the legacy-named resilience4j-spring-boot3
 * artifact, pulled in transitively by spring-cloud-starter-circuitbreaker-resilience4j).
 */
@SpringBootTest(properties = {
        "resilience4j.retry.instances.smokeTest.max-attempts=3",
        "resilience4j.retry.instances.smokeTest.wait-duration=10ms",
        "resilience4j.retry.instances.smokeTest.retry-exceptions=java.lang.IllegalStateException"
})
@ActiveProfiles("test")
class Resilience4jAnnotationSmokeTest {

    @Autowired
    private FlakyBean flakyBean;

    @Test
    void retryAnnotation_invokesMethodMaxAttemptsTimes_onBoot4() {
        assertThatThrownBy(() -> flakyBean.alwaysFails())
                .isInstanceOf(IllegalStateException.class);

        // 1 initial call + 2 retries == max-attempts(3). Proves the @Retry aspect is wired.
        assertThat(flakyBean.getInvocations()).isEqualTo(3);
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        FlakyBean flakyBean() {
            return new FlakyBean();
        }
    }

    static class FlakyBean {
        private final AtomicInteger invocations = new AtomicInteger();

        @Retry(name = "smokeTest")
        public void alwaysFails() {
            invocations.incrementAndGet();
            throw new IllegalStateException("boom");
        }

        int getInvocations() {
            return invocations.get();
        }
    }
}
