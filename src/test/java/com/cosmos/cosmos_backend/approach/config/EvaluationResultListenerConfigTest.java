package com.cosmos.cosmos_backend.approach.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EvaluationResultListenerConfigTest {

    @Configuration
    static class Dependencies {
        @Bean
        ConnectionFactory connectionFactory() {
            return org.mockito.Mockito.mock(ConnectionFactory.class);
        }

        @Bean
        EvaluationMqProperties evaluationMqProperties() {
            return new EvaluationMqProperties(
                    true, "evaluation.request", "evaluation.result", "evaluation.request.dlq",
                    java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(2),
                    java.time.Duration.ofSeconds(1), 50, java.time.Duration.ofSeconds(5), 4, 7, 3);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Dependencies.class, EvaluationResultListenerConfig.class);

    @Test
    void factory_isCreatedWithManualAckAndConfiguredValues_whenMqIsEnabled() {
        runner.withPropertyValues("evaluation.mq.enabled=true").run(context -> {
            SimpleRabbitListenerContainerFactory factory =
                    context.getBean("evaluationResultListenerFactory", SimpleRabbitListenerContainerFactory.class);
            assertThat(factory).isNotNull();
        });
    }

    @Test
    void factory_isNotCreated_whenMqIsDisabled() {
        runner.withPropertyValues("evaluation.mq.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("evaluationResultListenerFactory"));
    }
}
