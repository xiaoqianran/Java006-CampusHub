package com.shiqian.resource.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = MonitoringEndpointTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@AutoConfigureMockMvc
@AutoConfigureObservability
@org.springframework.test.context.ActiveProfiles("test")
class MonitoringEndpointTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceBusinessMetrics businessMetrics;

    @Test
    void shouldLoadPrometheusManagementConfig() {
        assertEquals("health,info,prometheus",
                environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true",
                environment.getProperty("management.endpoint.prometheus.enabled"));
        assertEquals("shiqian-resource",
                environment.getProperty("management.metrics.tags.application"));
        assertNotNull(meterRegistry);
    }

    @Test
    void shouldExposePrometheusEndpoint() throws Exception {
        businessMetrics.published();
        businessMetrics.audited(true);
        businessMetrics.searched(true);
        businessMetrics.downloaded();
        businessMetrics.uploadFailed();
        businessMetrics.rabbitConsumeFailed();
        businessMetrics.elasticsearchSyncFailed();
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("jvm_info"),
                        org.hamcrest.Matchers.containsString("resource_publish_total"),
                        org.hamcrest.Matchers.containsString("resource_audit_total"),
                        org.hamcrest.Matchers.containsString("resource_audit_reject_total"),
                        org.hamcrest.Matchers.containsString("resource_search_total"),
                        org.hamcrest.Matchers.containsString("resource_search_empty_total"),
                        org.hamcrest.Matchers.containsString("resource_download_total"),
                        org.hamcrest.Matchers.containsString("resource_upload_failure_total"),
                        org.hamcrest.Matchers.containsString("rabbitmq_consume_failure_total"),
                        org.hamcrest.Matchers.containsString("elasticsearch_sync_failure_total"))));
    }

    @Test
    void shouldNotExposeUnlistedEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ResourceBusinessMetrics.class)
    static class TestApplication {
    }
}
