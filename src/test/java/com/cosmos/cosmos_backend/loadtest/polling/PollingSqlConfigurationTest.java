package com.cosmos.cosmos_backend.loadtest.polling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PollingSqlConfigurationTest {
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }
    @Test void separatesNotificationStatusAndWorkerSql() throws Exception {
        var registry = new SimpleMeterRegistry();
        var probe = new PollingTelemetry(registry);
        probe.begin("poll-wait-1-test");
        @SuppressWarnings("unchecked") ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<PollingTelemetry> probes = mock(ObjectProvider.class);
        when(meters.getIfAvailable()).thenReturn(registry);
        when(probes.getIfAvailable()).thenReturn(probe);
        var processor = PollingSqlConfiguration.pollingSqlObserver(meters, probes);
        var source = (DataSource) processor.postProcessAfterInitialization(
                new DriverManagerDataSource("jdbc:h2:mem:polling-metrics"), "dataSource");
        String originalName = Thread.currentThread().getName();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/notifications")));
            statement.execute("select 1");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/solution-evaluations/42")));
            statement.execute("select 1");
            RequestContextHolder.resetRequestAttributes();
            Thread.currentThread().setName("evaluation-worker-1");
            statement.execute("select 1");
        } finally { Thread.currentThread().setName(originalName); }
        for (String flow : new String[]{"notification", "status", "evaluation"}) {
            assertEquals(1, registry.get("cosmos.polling.sql").tag("flow", flow).timer().count());
            assertEquals(1, registry.get("cosmos.polling.sql.statements").tag("flow", flow).counter().count());
            assertTrue(registry.get("cosmos.polling.sql").tag("flow", flow).timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
        }
    }
}
