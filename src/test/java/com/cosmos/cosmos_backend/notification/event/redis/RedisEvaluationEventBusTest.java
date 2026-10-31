package com.cosmos.cosmos_backend.notification.event.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cosmos.cosmos_backend.notification.event.EvaluationCompletedEvent;
import com.cosmos.cosmos_backend.notification.sse.SseEmitterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisEvaluationEventBusTest {

    private static final EvaluationCompletedEvent EVENT =
            new EvaluationCompletedEvent("evt-1", 42L, 9001L, "SUCCEEDED", 7001L);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SseEmitterRegistry registry;

    @InjectMocks
    private RedisEvaluationEventBus bus;

    private DefaultMessage messageOf(String body) {
        return new DefaultMessage(
                RedisEvaluationEventBus.CHANNEL.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encodeAndDecode_roundTripsEvent() {
        // When
        EvaluationCompletedEvent decoded = RedisEvaluationEventBus.decode(RedisEvaluationEventBus.encode(EVENT));

        // Then
        assertThat(decoded).isEqualTo(EVENT);
    }

    @Test
    void publish_sendsEncodedEventToChannel() {
        // When
        bus.publish(EVENT);

        // Then
        verify(redisTemplate).convertAndSend(RedisEvaluationEventBus.CHANNEL, "evt-1|42|9001|SUCCEEDED|7001");
    }

    @Test
    void onMessage_dispatchesDecodedEventToRegistry() {
        // When
        bus.onMessage(messageOf("evt-1|42|9001|SUCCEEDED|7001"), null);

        // Then
        verify(registry).dispatch(EVENT);
    }

    @Test
    void onMessage_ignoresMalformedMessage_withoutThrowing() {
        // When
        bus.onMessage(messageOf("broken"), null);

        // Then
        verifyNoInteractions(registry);
    }
}
