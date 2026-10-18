package com.cosmos.cosmos_backend.dailyBattle.worker;

import com.cosmos.cosmos_backend.dailyBattle.service.BattleGradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BattleGradingWorker {

    private final BattleGradingService battleGradingService;

    // 작업 선점, 채점 작업을 진행하는 워커
    public void gradingBattleSubmissions() {

        // 선점 작업 확인 (트랜잭션 1)
        Optional<Long> preemptParticipationId = battleGradingService.preemptSubmission();

        // 선점 작업 없으면 종료
        if(preemptParticipationId.isEmpty()){
            return;
        }

        // 선점 작업 있으면 채점으로 (트랜잭션 2)
        Long participationId = preemptParticipationId.get();

        try {
            battleGradingService.battleGrade(participationId);

        } catch (Exception e) {

            // 3. 채점 실패 상태 저장 (트랜잭션 3)
            battleGradingService.markGradingFailed(participationId);
        }
    }
}
