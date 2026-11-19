package com.cosmos.cosmos_backend.dailyBattle.worker;

import com.cosmos.cosmos_backend.dailyBattle.domain.GradingPreempt;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BattleGradingWorker {

    private final BattleGradingService battleGradingService;

    // 작업 선점, 채점 작업을 진행하는 워커
    public void gradingBattleSubmissions(Long battleId) {

        // 선점 작업 확인 (트랜잭션 1)
        Optional<GradingPreempt> preemptGrading = battleGradingService.preemptSubmission(battleId);

        // 선점 작업 없으면 종료
        if(preemptGrading.isEmpty()){
            return;
        }

        // 선점 작업 정보 (참여 id, 임대 토큰) 가져오기
        GradingPreempt preemptParticipation = preemptGrading.get();

        Long participationId = preemptParticipation.participationId();
        UUID leaseToken = preemptParticipation.leaseToken();

        // 선점한 작업으로 채점 진행
        try {
            battleGradingService.battleGrade(participationId, leaseToken);

        } catch (Exception e) {
            // 3. 채점 실패 상태 저장 (트랜잭션 3)
            battleGradingService.checkGradingFailed(participationId, leaseToken);
        }
    }
}
