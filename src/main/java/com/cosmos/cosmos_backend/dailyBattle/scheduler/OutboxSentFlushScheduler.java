package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.dailyBattle.domain.OutboxSentConfirm;
import com.cosmos.cosmos_backend.dailyBattle.service.OutboxSentConfirmBuffer;
import com.cosmos.cosmos_backend.dailyBattle.service.OutboxStatusChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
@ConditionalOnProperty(
        prefix = "app.outbox-publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxSentFlushScheduler {

    private static final int SENT_BATCH_SIZE = 100;

    private final OutboxSentConfirmBuffer outboxSentConfirmBuffer;

    private final OutboxStatusChangeService outboxStatusChangeService;

    // Confirm 성공 결과를 주기적으로 모아 SENT 상태로 변경
    @Scheduled(fixedDelayString = "${app.outbox-publisher.sent-flush-interval-ms:50}")
    public void flushSentEvents() {
        List<OutboxSentConfirm> confirmedEvents =
                outboxSentConfirmBuffer.drain(SENT_BATCH_SIZE);

        if (confirmedEvents.isEmpty()) {
            return;
        }

        try {
            int updatedCount = outboxStatusChangeService.changeOutboxSentBulk(confirmedEvents);

            if (updatedCount != confirmedEvents.size()) {
                log.warn(
                        "Outbox SENT bulk update count mismatch. requested={}, updated={}",
                        confirmedEvents.size(),
                        updatedCount
                );
            }
        } catch (RuntimeException exception) {
            // DB 오류가 발생하면 다음 주기에서 다시 처리
            outboxSentConfirmBuffer.addAll(confirmedEvents);
            log.error("Outbox SENT bulk update failed", exception);
        }
    }
}
