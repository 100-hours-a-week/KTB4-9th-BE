package com.cosmos.cosmos_backend.dailyBattle.consumer;

import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingConsumerPreemptResult;
import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingPreemptStatus;
import com.cosmos.cosmos_backend.dailyBattle.consumer.service.GradingPreemptService;
import com.cosmos.cosmos_backend.dailyBattle.domain.BattleAnswerPayload;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import com.cosmos.cosmos_backend.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "true"
)
public class BattleGradingConsumer {

    private final GradingPreemptService gradingPreemptService;

    private final BattleGradingService battleGradingService;

    private final RabbitTemplate rabbitTemplate;

    // GRADING_QUEUE(채점큐) 구독 + RabbitMQ 메시지 수신
    @RabbitListener(queues = RabbitMqConfig.GRADING_QUEUE, ackMode = "MANUAL")
    public void battleGradingMessageReceive(
            BattleAnswerPayload battleAnswerPayload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            // 재전달 횟수
            @Header(name = "x-delivery-count", required = false) Long deliveryCount,
            // 재시도 큐 횟수
            @Header(name = "x-death", required = false) List<Map<String, Object>> retryCount
    ) throws IOException {

        long redeliveryCount = deliveryCount == null ? 0 : deliveryCount;

        // 연결 끊김 재전달 3회 이상 → 실패큐 발행
        if (redeliveryCount >= 3) {
            gradingFailureMessagePublish(battleAnswerPayload);

            channel.basicAck(deliveryTag, false);

            return;
        }

        // 재시도 횟수 검사 (3회 도달시 실패 큐로)
        long totalRetryCount = getRetryCount(retryCount);

        if (totalRetryCount >= 3) {
            gradingFailureMessagePublish(battleAnswerPayload);

            channel.basicAck(deliveryTag, false);

            return;
        }

        // 채점 작업 선점
        GradingConsumerPreemptResult preemptResult;

        // DB 오류는 nack → 재시도 큐로
        try {
            preemptResult  = gradingPreemptService.preemptGradingTask(battleAnswerPayload.participationId());
        } catch (TransientDataAccessException | CannotCreateTransactionException exception){

            channel.basicNack(deliveryTag, false, false);

            return;
        }

        GradingPreemptStatus preemptStatus = preemptResult.gradingPreemptStatus();


        // 채점 결과에 따라 ACK / 재시도 처리

        // 선점 성공 → 기존 BattleGradingService를 이용해 채점
        if (preemptStatus == GradingPreemptStatus.PREEMPTED) {

            try {
                battleGradingService.battleGrade(
                        preemptResult.participationId(),
                        preemptResult.leaseToken()
                );
            } catch (Exception exception) {

                // 채점 실패시 NACK → 재시도
                channel.basicNack(deliveryTag, false, false);

                return;
            }

            channel.basicAck(deliveryTag, false);
            
            return;
        }


        // 이미 채점 완료, 실패 → 원본 ack
        if(preemptStatus == GradingPreemptStatus.ALREADY_COMPLETED || preemptStatus == GradingPreemptStatus.ALREADY_FAILED){

            channel.basicAck(deliveryTag, false);

            return;
        }

        // 다른 워커가 선점 중 + 임대 유효
        if(preemptStatus == GradingPreemptStatus.LEASE_ACTIVE){

            channel.basicNack(deliveryTag, false, false);

            return;
        }

        // 재선점 횟수 초과 or 유효하지 않은 상태
        if(preemptStatus == GradingPreemptStatus.OVER_LEASE_COUNT || preemptStatus == GradingPreemptStatus.INVALID_STATUS){
            // 최종 실패 큐에 참여 id와 함께..
            gradingFailureMessagePublish(battleAnswerPayload);

            channel.basicAck(deliveryTag, false);

            return;
        }
    }

    // 최종 실패 큐 발행 메소드
    private void gradingFailureMessagePublish(BattleAnswerPayload battleAnswerPayload) {
        CorrelationData failurePublshingConfirm = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.GRADING_FAILURE_EXCHANGE,
                RabbitMqConfig.GRADING_FAILURE_ROUTING_KEY,
                battleAnswerPayload,
                failurePublshingConfirm

        );

        CorrelationData.Confirm retryConfirm = failurePublshingConfirm.getFuture().orTimeout(3, TimeUnit.SECONDS).join();

        if (!retryConfirm.ack() || (failurePublshingConfirm.getReturned() != null)){
            throw new IllegalStateException(
                    "Battle_grading_failure_message_publish_failed"
            );
        }


    }

    // 재시도 횟수 확인 메소드
    private long getRetryCount(List<Map<String, Object>> retryCount) {

        if (retryCount == null) {
            return 0;
        }

        long totalRetryCount = 0;

        for (Map<String, Object> death : retryCount) {

            boolean retryQueue = RabbitMqConfig.GRADING_RETRY_QUEUE.equals(death.get("queue"));

            boolean ttlExpired = "expired".equals(death.get("reason"));

            if (retryQueue && ttlExpired) {

                Object count = death.get("count");

                if (count instanceof Number number) {
                    totalRetryCount += number.longValue();
                }
            }
        }

        return totalRetryCount;
    }
}
