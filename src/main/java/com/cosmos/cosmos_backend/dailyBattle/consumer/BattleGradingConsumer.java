package com.cosmos.cosmos_backend.dailyBattle.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "true"
)
public class BattleGradingConsumer {

    // TODO 1. RabbitMQ 메시지 수신

    // TODO 2. participationId로 채점 대상 선점

    // TODO 3. 기존 BattleGradingService를 이용해 채점

    // TODO 4. 채점 결과에 따라 ACK / 재시도 처리
}
