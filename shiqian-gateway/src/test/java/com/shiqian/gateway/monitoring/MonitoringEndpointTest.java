package com.shiqian.gateway.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = MonitoringEndpointTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration")
@AutoConfigureWebTestClient
@AutoConfigureObservability
@ActiveProfiles("test")
class MonitoringEndpointTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldLoadPrometheusManagementConfig() {
        assertEquals("health,info,prometheus",
                environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true",
                environment.getProperty("management.endpoint.prometheus.enabled"));
        assertEquals("shiqian-gateway",
                environment.getProperty("management.metrics.tags.application"));
        assertNotNull(meterRegistry);
    }

    @Test
    void shouldExposePrometheusEndpoint() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.hamcrest.MatcherAssert.assertThat(body,
                        org.hamcrest.Matchers.containsString("jvm_info")));
    }

    @Test
    void shouldNotExposeUnlistedEndpoint() {
        webTestClient.get()
                .uri("/actuator/env")
                .exchange()
                .expectStatus().isNotFound();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
