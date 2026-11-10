package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 평가 요청을 요청 큐로 보내고 발행 확인(Confirm)을 기다림. MQ를 켰을 때만 등록됨. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "evaluation.mq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EvaluationRequestPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final EvaluationMqProperties properties;

    /** 발행하고 브로커가 받았으면 true. 실패, 시간 초과, 큐 없음(라우팅 실패)이면 false. */
    public boolean publish(Long taskId, AiEvaluationRequest request) {
        try {
            // 1. 요청 본문을 만듦 (기존 AI 요청 JSON + taskId)
            ObjectNode body = jsonMapper.valueToTree(request);
            body.put("taskId", taskId);

            // 2. 메시지를 만듦 (JSON, 디스크 저장, 헤더에도 taskId를 넣음)
            Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(body))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setHeader("taskId", taskId)
                    .build();

            // 3. 기본 교환기로 요청 큐에 보내고 Confirm을 기다림
            CorrelationData correlation = new CorrelationData(taskId.toString());
            rabbitTemplate.send("", properties.requestQueue(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);

            // 4. 브로커가 받았고(ack) 큐로 라우팅도 됐을 때만 성공
            return confirm.ack() && correlation.getReturned() == null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("평가 요청 발행 중 인터럽트: taskId={}", taskId);
            return false;
        } catch (Exception e) {
            log.warn("평가 요청 발행 실패: taskId={}", taskId, e);
            return false;
        }
    }
}
