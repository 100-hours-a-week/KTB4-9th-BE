package com.cosmos.cosmos_backend.approach.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 평가 MQ 큐 선언. MQ를 켰을 때만 등록됨. */
@Configuration
@ConditionalOnProperty(prefix = "evaluation.mq", name = "enabled", havingValue = "true")
public class EvaluationMqQueueConfig {

    static final String DEAD_LETTER_EXCHANGE = "evaluation.dlx";

    // 만료·거절된 요청을 DLQ로 보내는 교환기
    @Bean
    DirectExchange evaluationDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // 요청 큐: 디스크 저장, 시간이 지나면 DLQ로 이동
    @Bean
    Queue evaluationRequestQueue(EvaluationMqProperties properties) {
        return QueueBuilder.durable(properties.requestQueue())
                .ttl((int) properties.requestTtl().toMillis())
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(properties.requestDlq())
                .build();
    }

    // 결과 큐
    @Bean
    Queue evaluationResultQueue(EvaluationMqProperties properties) {
        return QueueBuilder.durable(properties.resultQueue()).build();
    }

    // DLQ
    @Bean
    Queue evaluationRequestDlq(EvaluationMqProperties properties) {
        return QueueBuilder.durable(properties.requestDlq()).build();
    }

    // DLQ를 교환기에 연결
    @Bean
    Binding evaluationDlqBinding(Queue evaluationRequestDlq, DirectExchange evaluationDeadLetterExchange,
                                 EvaluationMqProperties properties) {
        return BindingBuilder.bind(evaluationRequestDlq)
                .to(evaluationDeadLetterExchange)
                .with(properties.requestDlq());
    }
}
