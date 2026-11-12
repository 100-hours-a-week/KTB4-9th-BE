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
import java.time.Clock;
import java.time.Instant;
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

    private final Clock clock;

    // GRADING_QUEUE(채점큐) 구독 + RabbitMQ 메시지 수신
    @RabbitListener(queues = RabbitMqConfig.GRADING_QUEUE, ackMode = "MANUAL")
    public void battleGradingMessageReceive(
            BattleAnswerPayload battleAnswerPayload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {

        // 채점 작업 선점
        GradingConsumerPreemptResult preemptResult;

        // DB 오류는 재시도
        try {
            preemptResult  = gradingPreemptService.preemptGradingTask(battleAnswerPayload.participationId());
        } catch (TransientDataAccessException | CannotCreateTransactionException exception){

            gradingRetryMessagePublish(battleAnswerPayload);

            channel.basicAck(deliveryTag, false);

            return;
        }

        GradingPreemptStatus preemptStatus = preemptResult.gradingPreemptStatus();


        // 채점 결과에 따라 ACK / 재시도 처리

        // 선점 성공 → 기존 BattleGradingService를 이용해 채점
        if (preemptStatus == GradingPreemptStatus.PREEMPTED) {

            // 시각 기록
            Instant processingStartedAt = clock.instant();

            try {
                battleGradingService.battleGrade(
                        preemptResult.participationId(),
                        preemptResult.leaseToken(),
                        processingStartedAt
                );
            } catch (Exception exception) {

                // 채점 실패시 Retry 발행 → confirm 확인 → 원본 ACK
                gradingRetryMessagePublish(battleAnswerPayload);

                channel.basicAck(deliveryTag, false);

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

            gradingRetryMessagePublish(battleAnswerPayload);

            channel.basicAck(deliveryTag, false);

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


    // 재시도 큐로 발행 메소드
    private void gradingRetryMessagePublish(BattleAnswerPayload battleAnswerPayload) {

        CorrelationData retryPublishingConfirm = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.GRADING_RETRY_EXCHANGE,
                RabbitMqConfig.GRADING_RETRY_ROUTING_KEY,
                battleAnswerPayload,
                retryPublishingConfirm
        );

        CorrelationData.Confirm retryConfirm = retryPublishingConfirm.getFuture().orTimeout(3, TimeUnit.SECONDS).join();

        if (!retryConfirm.ack() || retryPublishingConfirm.getReturned() != null){
            throw new IllegalStateException(
                    "Battle_grading_retry_message_publish_failed"
            );
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
}
