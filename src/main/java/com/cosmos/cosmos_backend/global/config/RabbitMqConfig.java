package com.cosmos.cosmos_backend.global.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    // 배틀 채점 설정값 관리
    public static final String GRADING_EXCHANGE = "battle.grading.exchange";
    public static final String GRADING_QUEUE = "battle.grading.queue";
    public static final String GRADING_ROUTING_KEY = "battle.grading.request";

    // 재시도 큐 설정값
    public static final String GRADING_RETRY_EXCHANGE = "battle.grading.retry.exchange";
    public static final String GRADING_RETRY_QUEUE = "battle.grading.retry.queue";
    public static final String GRADING_RETRY_ROUTING_KEY = "battle.grading.retry";

    // 실패 큐 설정값
    public static final String GRADING_FAILURE_EXCHANGE = "battle.grading.failure.exchange";
    public static final String GRADING_FAILURE_QUEUE = "battle.grading.failure.queue";
    public static final String GRADING_FAILURE_ROUTING_KEY = "battle.grading.failure";


    // 메세지 전달 시 json 바이트 <-> 자바 객체 변환 역할
    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }


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

    // 재시도 익스체인지
    @Bean
    public DirectExchange battleGradingRetryExchange() {
        return ExchangeBuilder
                .directExchange(GRADING_RETRY_EXCHANGE)
                .durable(true)
                .build();
    }

    // 재시도 큐 (TTL 2초)
    @Bean
    public Queue battleGradingRetryQueue() {
        return QueueBuilder
                .durable(GRADING_RETRY_QUEUE)
                .quorum()
                .ttl(3000)
                .deadLetterExchange(GRADING_EXCHANGE)
                .deadLetterRoutingKey(GRADING_ROUTING_KEY)
                .build();
    }

    // 재시도 큐, 익스체인지 바인딩
    @Bean
    public Binding battleGradingRetryBinding(
            Queue battleGradingRetryQueue,
            DirectExchange battleGradingRetryExchange
    ){
        return BindingBuilder
                .bind(battleGradingRetryQueue)
                .to(battleGradingRetryExchange)
                .with(GRADING_RETRY_ROUTING_KEY);
    }

    // 최종 실패 처리 익스체인지
    @Bean
    public DirectExchange gradingFailureExchange() {
        return ExchangeBuilder
                .directExchange(GRADING_FAILURE_EXCHANGE)
                .durable(true)
                .build();
    }

    // 최종 실패 큐
    @Bean
    public Queue gradingFailureQueue() {
        return QueueBuilder
                .durable(GRADING_FAILURE_QUEUE)
                .quorum()
                .build();
    }

    // 최종 싪패 큐, 익체 바인딩
    @Bean
    public Binding gradingFailureBinding(
            Queue gradingFailureQueue,
            DirectExchange gradingFailureExchange
    ){
        return BindingBuilder
                .bind(gradingFailureQueue)
                .to(gradingFailureExchange)
                .with(GRADING_FAILURE_ROUTING_KEY);

    }



}
