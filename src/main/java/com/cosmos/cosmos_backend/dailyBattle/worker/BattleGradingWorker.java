package com.cosmos.cosmos_backend.dailyBattle.worker;

import com.cosmos.cosmos_backend.dailyBattle.domain.entity.GradingPreempt;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BattleGradingWorker {

    private final BattleGradingService battleGradingService;

    // 부하테스트를 위한 메트릭
    private final BattleMetrics battleMetrics;

    // 부하테스트를 위한 메트릭
    private final Clock clock;

    // 작업 선점, 채점 작업을 진행하는 워커
    public void gradingBattleSubmissions() {

        // 선점 작업 확인 (트랜잭션 1)
        Optional<GradingPreempt> preemptGrading = battleGradingService.preemptSubmission();

        // 선점 작업 없으면 종료
        if(preemptGrading.isEmpty()){
            return;
        }

        // 선점 작업 정보 (참여 id, 임대 토큰) 가져오기
        GradingPreempt preemptParticipation = preemptGrading.get();

        Long participationId = preemptParticipation.participationId();
        UUID leaseToken = preemptParticipation.leaseToken();

        // 부하테스트를 위한 메트릭
        Instant processingStartedAt = clock.instant();

        try {
            battleGradingService.battleGrade(participationId, leaseToken, processingStartedAt);

        } catch (Exception e) {

            // 부하테스트를 위한 메트릭
            battleMetrics.recordProcessing(
                    Duration.between(processingStartedAt, clock.instant()),
                    "error"
            );

            // 3. 채점 실패 상태 저장 (트랜잭션 3)
            battleGradingService.checkGradingFailed(participationId);
        }
    }
}
