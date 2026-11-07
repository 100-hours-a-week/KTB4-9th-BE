package com.cosmos.cosmos_backend.global.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 배틀 채점 설정값 관리
    public static final String GRADING_EXCHANGE = "battle.grading.exchange";
    public static final String GRADING_QUEUE = "battle.grading.queue";
    public static final String GRADING_ROUTING_KEY = "battle.grading.request";


    // 배틀 채점 exchange
    @Bean
    public DirectExchange gradingExchange() {
        return ExchangeBuilder
                .directExchange(GRADING_EXCHANGE)
                .durable(true)
                .build();
    }

    // 영속성 설정 킨 큐
    @Bean
    public Queue gradingQueue() {
        return QueueBuilder
                .durable(GRADING_QUEUE)
                .quorum()
                .build();
    }

    // 채점 익스체인지 - 큐 바인딩
    @Bean
    public Binding gradingBinding(
            Queue gradingQueue,
            DirectExchange gradingExchange
    ) {
        return BindingBuilder
                .bind(gradingQueue)
                .to(gradingExchange)
                .with(GRADING_ROUTING_KEY);
    }

}
