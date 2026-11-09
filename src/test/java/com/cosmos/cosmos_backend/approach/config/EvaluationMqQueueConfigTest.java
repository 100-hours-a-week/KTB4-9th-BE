package com.cosmos.cosmos_backend.approach.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EvaluationMqQueueConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EvaluationWorkerConfig.class, EvaluationMqQueueConfig.class);

    @Test
    void doesNotDeclareAnything_whenMqIsDisabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(Queue.class);
            assertThat(context).doesNotHaveBean(DirectExchange.class);
            assertThat(context).doesNotHaveBean(Binding.class);
        });
    }

    @Test
    void doesNotDeclareAnything_whenMqSettingIsMissing() {
        // When & Then
        runner.run(context -> assertThat(context).doesNotHaveBean(Queue.class));
    }

    @Test
    void declaresQueuesExchangeAndBinding_whenMqIsEnabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=true").run(context -> {
            assertThat(context.getBeansOfType(Queue.class)).containsOnlyKeys(
                    "evaluationRequestQueue", "evaluationResultQueue", "evaluationRequestDlq");
            assertThat(context).hasSingleBean(DirectExchange.class);
            assertThat(context).hasSingleBean(Binding.class);
        });
    }

    @Test
    void requestQueueExpiresIntoDlq_whenMqIsEnabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=true").run(context -> {
            Queue requestQueue = context.getBean("evaluationRequestQueue", Queue.class);

            assertThat(requestQueue.isDurable()).isTrue();
            assertThat(requestQueue.getArguments())
                    .containsEntry("x-message-ttl", 300_000)
                    .containsEntry("x-dead-letter-exchange", "evaluation.dlx")
                    .containsEntry("x-dead-letter-routing-key", "evaluation.request.dlq");
        });
    }

    @Test
    void bindsDlqToDeadLetterExchange_whenMqIsEnabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=true").run(context -> {
            Binding binding = context.getBean(Binding.class);

            assertThat(binding.getExchange()).isEqualTo("evaluation.dlx");
            assertThat(binding.getDestination()).isEqualTo("evaluation.request.dlq");
            assertThat(binding.getRoutingKey()).isEqualTo("evaluation.request.dlq");
        });
    }
}
