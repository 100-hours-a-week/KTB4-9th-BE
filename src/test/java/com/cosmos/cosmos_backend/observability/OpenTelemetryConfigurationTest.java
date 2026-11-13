package com.cosmos.cosmos_backend.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class OpenTelemetryConfigurationTest {

    @Test
    void runtimeImagePinsAndStartsTheOpenTelemetryAgentWithSafeDefaults() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String image = Files.readString(Path.of("dockerfile"));

        assertThat(build).contains("io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.1");
        assertThat(image).contains("/opt/opentelemetry-javaagent.jar");
        assertThat(image).contains("-javaagent:/opt/opentelemetry-javaagent.jar");
        assertThat(image).contains("OTEL_TRACES_EXPORTER=none");
        assertThat(image).contains("OTEL_METRICS_EXPORTER=none");
        assertThat(image).contains("OTEL_LOGS_EXPORTER=none");
        assertThat(image).contains("OTEL_PROPAGATORS=tracecontext,baggage");
    }

    @Test
    void applicationUsesStructuredConsoleLogsAndKeepsPrometheusMetrics() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new FileSystemResource("src/main/resources/application.yaml"));

        assertThat(value(sources, "logging.structured.format.console")).isEqualTo("logstash");
        assertThat(value(sources, "management.endpoint.prometheus.enabled")).isEqualTo(true);
        assertThat(value(sources, "management.endpoints.web.exposure.include")).isEqualTo("health, info, prometheus");
    }

    private Object value(List<PropertySource<?>> sources, String key) {
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
