package com.cosmos.cosmos_backend.dailyBattle.consumer;

import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingConsumerPreemptResult;
import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingPreemptStatus;
import com.cosmos.cosmos_backend.dailyBattle.consumer.service.GradingPreemptService;
import com.cosmos.cosmos_backend.dailyBattle.domain.BattleAnswerPayload;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import com.cosmos.cosmos_backend.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import com.cosmos.cosmos_backend.global.config.RabbitMqConfig;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

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
        GradingConsumerPreemptResult preemptResult = gradingPreemptService.preemptGradingTask(battleAnswerPayload.participationId());

        GradingPreemptStatus preemptStatus = preemptResult.gradingPreemptStatus();


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
                // Retry 발행 → confirm 확인 → 원본 ACK
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.GRADING_RETRY_EXCHANGE,
                        RabbitMqConfig.GRADING_RETRY_ROUTING_KEY,
                        battleAnswerPayload
                );
                return;
            }

            channel.basicAck(deliveryTag, false);
            
            return;
        }


        // TODO 4. 채점 결과에 따라 ACK / 재시도 처리


        //channel.basicAck(deliveryTag, false);
    }

}
