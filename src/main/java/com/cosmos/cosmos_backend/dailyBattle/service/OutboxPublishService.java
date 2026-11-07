package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.OutboxEvent;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.OutboxEventPreemptResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class OutboxPublishService {

    private final OutboxStatusChangeService outboxStatusChangService;

    private final RabbitTemplate rabbitTemplate;

    // 이벤트 발행 메소드
    public void eventPublish(){
        // 배치로 선점하고 + 발행 중 상태로 변경 (트랜잭션 1)
        OutboxEventPreemptResponseDto outboxEvents = outboxStatusChangService.preemptOutboxEvent();

        // RabbitMQ로 발행
        for (OutboxEvent event : outboxEvents.outboxEventList()) {

            // 메시지 발행 확인 응답 콜백으로 확인
            CorrelationData publishConfirmData = new CorrelationData(event.getOutboxEventId().toString());

            publishConfirmData.getFuture().whenComplete((confirm, exception) -> {
                if (exception != null) {
                    // 예외 발생시 재선점 되도록
                    return;
                }

                // ack 수신 확인 & 라우팅 성공 확인하면 발행 완료 처리
                if (confirm.ack() && publishConfirmData.getReturned() == null) {
                    outboxStatusChangService.changeOutboxSent(event.getOutboxEventId(), outboxEvents.leaseToken());
                } else {
                    // NACK 수신
                    // Outbox 재시도 대상으로 유지
                }
            });

            rabbitTemplate.convertAndSend(
                    "battle.grading.exchange",
                    "battle.grading.request",
                    event.getBattleAnswerPayload(),
                    publishConfirmData
            );

        }


    }
}
