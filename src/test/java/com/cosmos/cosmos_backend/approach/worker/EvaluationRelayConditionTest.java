package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cosmos.cosmos_backend.approach.config.EvaluationWorkerConfig;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationRelayService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class EvaluationRelayConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EvaluationWorkerConfig.class, EvaluationRequestPublisher.class, EvaluationRelay.class)
            .withBean(SolutionEvaluationRelayService.class, () -> mock(SolutionEvaluationRelayService.class))
            .withBean(SolutionEvaluationExecutionService.class, () -> mock(SolutionEvaluationExecutionService.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void relayAndPublisherAreNotRegistered_whenMqIsDisabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(EvaluationRelay.class);
            assertThat(context).doesNotHaveBean(EvaluationRequestPublisher.class);
        });
    }

    @Test
    void relayAndPublisherAreRegistered_whenMqIsEnabled() {
        // When & Then
        runner.withPropertyValues("evaluation.mq.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(EvaluationRelay.class);
            assertThat(context).hasSingleBean(EvaluationRequestPublisher.class);
        });
    }
}
