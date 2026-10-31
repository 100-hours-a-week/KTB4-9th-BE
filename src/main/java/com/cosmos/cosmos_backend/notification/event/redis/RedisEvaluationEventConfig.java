package com.cosmos.cosmos_backend.notification.event.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 평가 완료 채널 구독 설정. */
@Configuration
@ConditionalOnProperty(name = "evaluation.event.transport", havingValue = "redis")
public class RedisEvaluationEventConfig {

    @Bean
    RedisMessageListenerContainer evaluationEventListenerContainer(
            RedisConnectionFactory connectionFactory, RedisEvaluationEventBus bus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(bus, new ChannelTopic(RedisEvaluationEventBus.CHANNEL));
        return container;
    }
}
