package com.cosmos.cosmos_backend.loadtest.polling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class PollingSqlConfiguration {
    @Bean
    static BeanPostProcessor pollingSqlObserver(ObjectProvider<MeterRegistry> registries,
                                               ObjectProvider<PollingTelemetry> probes) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof DataSource source)) return bean;
                return ProxyDataSourceBuilder.create(source).name("polling-sql").listener(new QueryExecutionListener() {
                    @Override public void beforeQuery(ExecutionInfo info, List<QueryInfo> queries) {
                        info.addCustomValue("pollingStartNanos", System.nanoTime());
                    }
                    @Override public void afterQuery(ExecutionInfo info, List<QueryInfo> queries) {
                        MeterRegistry registry = registries.getIfAvailable();
                        PollingTelemetry probe = probes.getIfAvailable();
                        if (registry == null || probe == null) return;
                        String flow = "other";
                        if (Thread.currentThread().getName().startsWith("evaluation-worker-")) flow = "evaluation";
                        else if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                            String path = attrs.getRequest().getRequestURI();
                            if (path.startsWith("/solution-evaluations/")) flow = "status";
                            else if (path.equals("/notifications") && attrs.getRequest().getMethod().equals("GET")) flow = "notification";
                            else if (path.matches("/problems/[0-9]+/solution-submissions")) flow = "submit";
                        }
                        String[] tags = {"flow", flow, "run", probe.run(), "outcome", info.isSuccess() ? "success" : "error"};
                        Long start = info.getCustomValue("pollingStartNanos", Long.class);
                        Timer.builder("cosmos.polling.sql").tags(tags).register(registry)
                                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                        long statements = queries.stream().mapToLong(q -> Math.max(1, q.getParametersList().size())).sum();
                        registry.counter("cosmos.polling.sql.statements", tags).increment(statements);
                    }
                }).build();
            }
        };
    }
}
