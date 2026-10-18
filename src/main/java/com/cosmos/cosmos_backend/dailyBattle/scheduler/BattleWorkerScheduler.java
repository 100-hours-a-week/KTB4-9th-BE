package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import com.cosmos.cosmos_backend.dailyBattle.worker.BattleGradingWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class BattleWorkerScheduler {

    private final BattleResultService battleResultService;

    private final BattleParticipationRepository battleParticipationRepository;

    private final DailyBattleRepository dailyBattleRepository;

    private final Clock clock;

    private final TaskScheduler taskScheduler;

    private final BattleGradingWorker battleGradingWorker;

    private List<ScheduledFuture<?>> workerTasks = new ArrayList<>();

    private ScheduledFuture<?> recheckTask;

    // 매일 12:00에 채점 워커 시작
    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
    public void startGradingWorker(){

        // worker 작업 2개 등록
        for (int i = 0; i < 2; i++) {

            ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(
                    battleGradingWorker::gradingBattleSubmissions,
                    Duration.ofMillis(100)
            );

            workerTasks.add(task);
        }
    }

    // worker 종료
    public void stopGradingWorker() {

        for (ScheduledFuture<?> task : workerTasks) {
            task.cancel(false);
        }

        workerTasks.clear();
    }

    // 12:10 결과 공개 시점에 실행할 결과 확정 메소드
    @Scheduled(cron = "00 10 12 * * *", zone = "Asia/Seoul")
    public void finalizeBattle() {

        LocalDate today = LocalDate.now(clock);

        Long todayBattleId = dailyBattleRepository.findIdByBattleDate(today).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "today_battle_not_exist"
        ));


        // 1. 남아 있는 채점 작업 확인 (제출 상태, 채점 중 상태 있는지 확인)
        Boolean existRemainedGrading = battleParticipationRepository.existsByDailyBattleIdAndParticipationStatusIn(
                todayBattleId,
                List.of(ParticipationStatus.SUBMITTED, ParticipationStatus.GRADING)
        );

        // 2. 모든 채점 완료 여부 확인
        // 2-1. 남은 채점 있다면 재확인 작업 등록하고 실행
        if(existRemainedGrading){

            if(recheckTask == null){
                recheckTask = taskScheduler.scheduleWithFixedDelay(
                        this::finalizeBattle,
                        Duration.ofMillis(100)
                );
            }

            return;
        }

        // 3. 모든 채점 완료 → 결과 공개 상태 저장
        LocalDateTime publishedAt = LocalDateTime.now(clock);

        battleResultService.battleResultPublished(todayBattleId, publishedAt);

        // 4. 남은 채점 없으면 worker들 종료
        stopGradingWorker();

        if(recheckTask != null){
            recheckTask.cancel(false);
            recheckTask = null;
        }
    }

}
