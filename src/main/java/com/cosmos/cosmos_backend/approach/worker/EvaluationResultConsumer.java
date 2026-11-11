package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 결과 큐에서 AI 평가 결과를 받아 반영하는 소비자. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "evaluation.mq", name = "enabled", havingValue = "true")
public class EvaluationResultConsumer {

    private final SolutionEvaluationExecutionService executionService;
    private final JsonMapper jsonMapper;
    private final EvaluationMqProperties properties;

    @RabbitListener(queues = "${evaluation.mq.result-queue:evaluation.result}",
            containerFactory = "evaluationResultListenerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();

        // 1. 본문을 읽음 (읽을 수 없으면 어떤 작업인지 몰라서 버리고, 7분 안전망이 처리)
        EvaluationResultMessage result;
        try {
            result = jsonMapper.readValue(message.getBody(), EvaluationResultMessage.class);
            if (result == null || result.taskId() == null) {
                throw new IllegalArgumentException("taskId missing");
            }
        } catch (Exception e) {
            log.error("평가 결과 메시지를 읽을 수 없어 버림", e);
            channel.basicReject(tag, false);
            return;
        }

        // 2. 반영하고, 끝났으면 ACK. DB 장애로 기록조차 못 하면 ACK하지 않고 되돌려 다시 받음
        if (process(result)) {
            channel.basicAck(tag, false);
        } else {
            channel.basicNack(tag, false, true);
        }
    }

    // 결과를 반영 (일시 오류는 정해진 횟수까지 재시도, 그래도 안 되면 INTERNAL_ERROR로 기록). 처리가 끝났으면 true
    private boolean process(EvaluationResultMessage result) {
        Exception last = null;
        for (int attempt = 1; attempt <= properties.resultMaxAttempts(); attempt++) {
            try {
                executionService.applyResult(result);
                return true;
            } catch (BusinessException e) {
                // 한도 초과 같은 규칙 위반은 다시 해도 같은 결과라 바로 실패 기록
                last = e;
                break;
            } catch (Exception e) {
                log.warn("평가 결과 반영 실패: taskId={}, attempt={}", result.taskId(), attempt, e);
                last = e;
            }
        }

        try {
            executionService.recordInternalError(result.taskId(), last.getClass().getSimpleName());
            return true;
        } catch (Exception e) {
            log.error("평가 실패 기록도 실패해 보류: taskId={}", result.taskId(), e);
            return false;
        }
    }
}
