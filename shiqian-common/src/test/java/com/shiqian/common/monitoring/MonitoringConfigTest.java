package com.shiqian.common.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringConfigTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Yaml YAML = new Yaml();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldConfigurePrometheusAndGrafanaServices() throws IOException {
        Map<String, Object> compose = loadYaml(ROOT.resolve("docker-compose.yml"));
        Map<String, Object> services = asMap(compose.get("services"));
        Map<String, Object> volumes = asMap(compose.get("volumes"));

        assertTrue(services.containsKey("prometheus"));
        assertTrue(services.containsKey("grafana"));
        assertTrue(services.containsKey("redis-exporter"));
        assertTrue(services.containsKey("elasticsearch-exporter"));
        assertTrue(volumes.containsKey("prometheus-data"));
        assertTrue(volumes.containsKey("grafana-data"));
    }

    @Test
    void shouldScrapeAllApplicationServices() throws IOException {
        Map<String, Object> prometheus = loadYaml(
                ROOT.resolve("docker/prometheus/prometheus.yml"));
        List<Map<String, Object>> scrapeConfigs = asList(
                prometheus.get("scrape_configs"));

        Set<String> jobNames = scrapeConfigs.stream()
                .map(config -> (String) config.get("job_name"))
                .collect(Collectors.toSet());
        Set<String> targets = scrapeConfigs.stream()
                .flatMap(config -> asList(config.get("static_configs")).stream())
                .flatMap(config -> asObjectList(config.get("targets")).stream())
                .map(String.class::cast)
                .collect(Collectors.toSet());

        assertTrue(jobNames.contains("shiqian-gateway"));
        assertTrue(jobNames.contains("shiqian-user"));
        assertTrue(jobNames.contains("shiqian-resource"));
        assertTrue(jobNames.contains("redis"));
        assertTrue(jobNames.contains("rabbitmq"));
        assertTrue(jobNames.contains("elasticsearch"));
        assertTrue(targets.contains("host.docker.internal:8080"));
        assertTrue(targets.contains("host.docker.internal:8081"));
        assertTrue(targets.contains("host.docker.internal:8082"));
        assertTrue(targets.contains("redis-exporter:9121"));
        assertTrue(targets.contains("rabbitmq:15692"));
        assertTrue(targets.contains("elasticsearch-exporter:9114"));
    }

    @Test
    void shouldProvisionGrafanaDatasourceAndDashboardProvider() throws IOException {
        Map<String, Object> datasource = loadYaml(ROOT.resolve(
                "docker/grafana/provisioning/datasources/prometheus.yml"));
        Map<String, Object> provider = loadYaml(ROOT.resolve(
                "docker/grafana/provisioning/dashboards/shiqian.yml"));

        Map<String, Object> prometheus = asMap(asList(
                datasource.get("datasources")).get(0));
        Map<String, Object> dashboardProvider = asMap(asList(
                provider.get("providers")).get(0));

        assertEquals("Prometheus", prometheus.get("name"));
        assertEquals("http://prometheus:9090", prometheus.get("url"));
        assertEquals("/var/lib/grafana/dashboards",
                asMap(dashboardProvider.get("options")).get("path"));
    }

    @Test
    void shouldCoverJvmAndHttpDashboardPanels() throws IOException {
        JsonNode dashboard = OBJECT_MAPPER.readTree(ROOT.resolve(
                "docker/grafana/dashboards/shiqian-jvm-http.json").toFile());
        List<String> expressions = StreamSupport.stream(
                        dashboard.get("panels").spliterator(), false)
                .flatMap(panel -> StreamSupport.stream(
                        panel.get("targets").spliterator(), false))
                .map(target -> target.get("expr").asText())
                .toList();

        assertMetricCovered(expressions, "jvm_memory_used_bytes");
        assertMetricCovered(expressions, "jvm_gc_pause_seconds_count");
        assertMetricCovered(expressions, "jvm_threads_live_threads");
        assertMetricCovered(expressions, "http_server_requests_seconds_bucket");
        assertMetricCovered(expressions, "http_server_requests_seconds_count");
        assertEquals(6, dashboard.get("panels").size());
    }

    @Test
    void shouldCoverBusinessMetricsAndAlertRules() throws IOException {
        JsonNode dashboard = OBJECT_MAPPER.readTree(ROOT.resolve(
                "docker/grafana/dashboards/shiqian-business.json").toFile());
        List<String> expressions = StreamSupport.stream(
                        dashboard.get("panels").spliterator(), false)
                .flatMap(panel -> StreamSupport.stream(
                        panel.get("targets").spliterator(), false))
                .map(target -> target.get("expr").asText())
                .toList();
        for (String metric : List.of(
                "resource_publish_total",
                "resource_audit_total",
                "resource_audit_reject_total",
                "resource_search_total",
                "resource_search_empty_total",
                "resource_download_total",
                "resource_upload_failure_total",
                "rabbitmq_consume_failure_total",
                "elasticsearch_sync_failure_total")) {
            assertMetricCovered(expressions, metric);
        }

        Map<String, Object> alerts = loadYaml(
                ROOT.resolve("docker/prometheus/alerts.yml"));
        assertTrue(asObjectList(alerts.get("groups")).size() > 0);
    }

    private static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return YAML.load(inputStream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjectList(Object value) {
        return (List<Object>) value;
    }

    private static void assertMetricCovered(List<String> expressions, String metric) {
        assertTrue(expressions.stream().anyMatch(expression -> expression.contains(metric)));
    }
}
