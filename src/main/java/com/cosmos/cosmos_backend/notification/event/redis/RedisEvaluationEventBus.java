package com.cosmos.cosmos_backend.notification.event.redis;

import com.cosmos.cosmos_backend.notification.event.EvaluationCompletedEvent;
import com.cosmos.cosmos_backend.notification.event.EvaluationEventBus;
import com.cosmos.cosmos_backend.notification.sse.SseEmitterRegistry;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis Pub/Sub으로 이벤트를 모든 서버에 전달. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "evaluation.event.transport", havingValue = "redis")
public class RedisEvaluationEventBus implements EvaluationEventBus, MessageListener {

    static final String CHANNEL = "evaluation-completed";

    private final StringRedisTemplate redisTemplate;
    private final SseEmitterRegistry registry;

    @Override
    public void publish(EvaluationCompletedEvent event) {
        redisTemplate.convertAndSend(CHANNEL, encode(event));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            registry.dispatch(decode(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("평가 완료 이벤트 수신 처리 실패", e);
        }
    }

    // 값 5개를 | 로 이어 붙임
    static String encode(EvaluationCompletedEvent event) {
        return String.join("|", event.eventId(), String.valueOf(event.userId()),
                String.valueOf(event.evaluationTaskId()), event.status(), String.valueOf(event.notificationId()));
    }

    // | 로 나눠 다시 이벤트로 만듦
    static EvaluationCompletedEvent decode(String text) {
        String[] parts = text.split("\\|");
        return new EvaluationCompletedEvent(parts[0], Long.valueOf(parts[1]), Long.valueOf(parts[2]),
                parts[3], Long.valueOf(parts[4]));
    }
}
