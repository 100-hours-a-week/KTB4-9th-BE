package com.cosmos.cosmos_backend.approach.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EvaluationResultConsumerTest {

    private static final long TAG = 7L;

    @Mock
    private SolutionEvaluationExecutionService executionService;

    @Mock
    private Channel channel;

    private EvaluationResultConsumer consumer;

    @BeforeEach
    void setUp() {
        EvaluationMqProperties properties = new EvaluationMqProperties(
                true, "evaluation.request", "evaluation.result", "evaluation.request.dlq", Duration.ofMinutes(5),
                Duration.ofSeconds(10), Duration.ofMinutes(2), Duration.ofSeconds(1), 50, Duration.ofSeconds(5), 2, 10, 3);
        consumer = new EvaluationResultConsumer(executionService, JsonMapper.builder().build(), properties);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        return new Message(body.getBytes(), properties);
    }

    private static final String SUCCESS_BODY =
            "{\"taskId\":5,\"status\":\"SUCCESS\",\"score\":85,\"llmFeedback\":\"좋아요\","
                    + "\"keywords\":[{\"keyword\":\"해시맵\",\"isIncluded\":true}],\"extra\":\"무시\"}";

    @Test
    void onMessage_appliesResult_andAcks() throws IOException {
        // When
        consumer.onMessage(message(SUCCESS_BODY), channel);

        // Then
        verify(executionService).applyResult(any());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void onMessage_rejectsWithoutRequeue_whenBodyIsNotReadable() throws IOException {
        // When
        consumer.onMessage(message("이건 JSON이 아님"), channel);

        // Then
        verifyNoInteractions(executionService);
        verify(channel).basicReject(TAG, false);
    }

    @Test
    void onMessage_rejectsWithoutRequeue_whenTaskIdIsMissing() throws IOException {
        // When
        consumer.onMessage(message("{\"status\":\"SUCCESS\"}"), channel);

        // Then
        verifyNoInteractions(executionService);
        verify(channel).basicReject(TAG, false);
    }

    @Test
    void onMessage_retriesThenAcks_whenFailureIsTemporary() throws IOException {
        // Given
        doThrow(new IllegalStateException("일시 오류")).doNothing().when(executionService).applyResult(any());

        // When
        consumer.onMessage(message(SUCCESS_BODY), channel);

        // Then
        verify(executionService, times(2)).applyResult(any());
        verify(executionService, never()).recordInternalError(anyLong(), anyString());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void onMessage_recordsInternalErrorAndAcks_whenRetriesAreExhausted() throws IOException {
        // Given
        doThrow(new IllegalStateException("계속 실패")).when(executionService).applyResult(any());

        // When
        consumer.onMessage(message(SUCCESS_BODY), channel);

        // Then
        verify(executionService, times(3)).applyResult(any());
        verify(executionService).recordInternalError(5L, "IllegalStateException");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void onMessage_recordsInternalErrorWithoutRetry_whenRuleIsViolated() throws IOException {
        // Given
        doThrow(new BusinessException(HttpStatus.CONFLICT, "limit")).when(executionService).applyResult(any());

        // When
        consumer.onMessage(message(SUCCESS_BODY), channel);

        // Then
        verify(executionService, times(1)).applyResult(any());
        verify(executionService).recordInternalError(5L, "BusinessException");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void onMessage_nacksWithRequeue_whenFailureCannotBeRecorded() throws IOException {
        // Given
        doThrow(new IllegalStateException("DB 장애")).when(executionService).applyResult(any());
        doThrow(new IllegalStateException("DB 장애")).when(executionService).recordInternalError(anyLong(), anyString());

        // When
        consumer.onMessage(message(SUCCESS_BODY), channel);

        // Then
        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
