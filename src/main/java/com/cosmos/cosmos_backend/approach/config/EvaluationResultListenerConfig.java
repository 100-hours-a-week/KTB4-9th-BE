package com.cosmos.cosmos_backend.approach.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 결과 소비자가 쓰는 리스너 설정. MQ가 켜졌을 때만 만듦. */
@Configuration
@ConditionalOnProperty(prefix = "evaluation.mq", name = "enabled", havingValue = "true")
public class EvaluationResultListenerConfig {

    @Bean
    SimpleRabbitListenerContainerFactory evaluationResultListenerFactory(
            ConnectionFactory connectionFactory, EvaluationMqProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 1. 처리를 끝낸 뒤 코드에서 직접 ACK
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 2. 동시에 돌릴 스레드 수와 미리 받아 둘 메시지 수
        factory.setConcurrentConsumers(properties.resultConcurrency());
        factory.setPrefetchCount(properties.resultPrefetch());
        return factory;
    }
}
