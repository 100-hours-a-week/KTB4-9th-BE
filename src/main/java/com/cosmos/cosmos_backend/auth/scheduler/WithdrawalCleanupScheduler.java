package com.cosmos.cosmos_backend.auth.scheduler;

import com.cosmos.cosmos_backend.auth.service.WithdrawalCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class WithdrawalCleanupScheduler {

    private final WithdrawalCleanupService cleanupService;

    // 매일 새벽 3시에 탈퇴 후 30일 지난 사용자 데이터 영구 삭제
    @Scheduled(cron = "${withdrawal.cleanup-cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void cleanupWithdrawnUsers() {

        int deletedCount;

        // 한 번에 최대 100명씩 삭제
        do {
            deletedCount = cleanupService.deleteExpiredUsers();
        } while (deletedCount == 100);
    }
}