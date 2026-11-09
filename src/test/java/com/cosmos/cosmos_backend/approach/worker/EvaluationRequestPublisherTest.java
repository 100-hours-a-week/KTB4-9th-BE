package com.cosmos.cosmos_backend.approach.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EvaluationRequestPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private EvaluationRequestPublisher publisher;

    @BeforeEach
    void setUp() {
        EvaluationMqProperties properties = new EvaluationMqProperties(
                true, "evaluation.request", "evaluation.result", "evaluation.request.dlq", Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofMinutes(2), Duration.ofSeconds(1), 50, Duration.ofMillis(100));
        publisher = new EvaluationRequestPublisher(rabbitTemplate, JsonMapper.builder().build(), properties);
    }

    private AiEvaluationRequest request() {
        return new AiEvaluationRequest("제목", "내용", "ARRAY", "이유", List.of("키워드"), List.of(), List.of(), "이렇게 풉니다");
    }

    // send가 호출되면 주어진 방식으로 Confirm을 완료시킴
    private void answerSendWith(Consumer<CorrelationData> completer) {
        doAnswer(invocation -> {
            completer.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void publish_returnsTrue_andSendsJsonWithTaskId_whenBrokerAcks() throws Exception {
        // Given
        answerSendWith(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));

        // When
        boolean published = publisher.publish(7L, request());

        // Then
        assertThat(published).isTrue();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq("evaluation.request"), captor.capture(), any(CorrelationData.class));
        Message message = captor.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"taskId\":7")
                .contains("\"problemTitle\":\"제목\"")
                .contains("\"naturalSolution\":\"이렇게 풉니다\"");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getHeaders()).containsEntry("taskId", 7L);
    }

    @Test
    void publish_returnsFalse_whenBrokerNacks() {
        // Given
        answerSendWith(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack")));

        // When & Then
        assertThat(publisher.publish(7L, request())).isFalse();
    }

    @Test
    void publish_returnsFalse_whenMessageIsReturnedBecauseQueueIsMissing() {
        // Given
        answerSendWith(correlation -> {
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0]), 312, "NO_ROUTE", "", "evaluation.request"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        // When & Then
        assertThat(publisher.publish(7L, request())).isFalse();
    }

    @Test
    void publish_returnsFalse_whenConfirmDoesNotArriveInTime() {
        // Given: Confirm을 완료시키지 않음
        answerSendWith(correlation -> { });

        // When & Then
        assertThat(publisher.publish(7L, request())).isFalse();
    }

    @Test
    void publish_returnsFalse_whenBrokerConnectionFails() {
        // Given
        doThrow(new AmqpConnectException(new RuntimeException("refused")))
                .when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        // When & Then
        assertThat(publisher.publish(7L, request())).isFalse();
    }
}
