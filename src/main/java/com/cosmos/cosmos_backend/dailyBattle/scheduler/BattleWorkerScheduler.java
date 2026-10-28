package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleMetrics;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class BattleWorkerScheduler {

    private final BattleResultService battleResultService;

    private final DailyBattleService dailyBattleService;

    private final BattleParticipationRepository battleParticipationRepository;

    private final DailyBattleRepository dailyBattleRepository;

    private final Clock clock;

    private final TaskScheduler taskScheduler;

    private final BattleGradingWorker battleGradingWorker;

    // 부하테스트를 위한 메트릭
    private final BattleMetrics battleMetrics;

    // 부하테스트를 위한 메트릭
    private final BattleSchedule battleSchedule;

    private List<ScheduledFuture<?>> workerTasks = new ArrayList<>();

    private ScheduledFuture<?> recheckTask;

    // 설정된 배틀 시작 시각에 채점 워커 시작
    @Scheduled(cron = "#{@battleSchedule.openCron()}", zone = "Asia/Seoul")
    public void startGradingWorker(){

        // 부하테스트를 위한 메트릭
        battleMetrics.resetBattleRun();

        // worker 작업 2개 등록
        for (int i = 0; i < 2; i++) {

            ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(
                    battleGradingWorker::gradingBattleSubmissions,
                    Duration.ofMillis(100)
            );

            workerTasks.add(task);
        }

        // 부하테스트를 위한 메트릭
        battleMetrics.setActiveWorkers(workerTasks.size());
    }

    // worker 종료
    public void stopGradingWorker() {

        for (ScheduledFuture<?> task : workerTasks) {
            task.cancel(false);
        }

        workerTasks.clear();
        // 부하테스트를 위한 메트릭
        battleMetrics.setActiveWorkers(0);
    }

    // 설정된 결과 공개 시각에 실행할 결과 확정 메소드
    @Scheduled(cron = "#{@battleSchedule.resultCron()}", zone = "Asia/Seoul")
    public void finalizeBattle() {

        // 부하테스트를 위한 메트릭
        battleMetrics.recordFinalizeAttempt();

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
        // 부하테스트를 위한 메트릭
        battleMetrics.recordResultPublished(
                today.atTime(battleSchedule.resultTime())
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                publishedAt.atZone(ZoneId.of("Asia/Seoul")).toInstant()
        );

        // 4. 남은 채점 없으면 worker들 종료
        stopGradingWorker();

        if(recheckTask != null){
            recheckTask.cancel(false);
            recheckTask = null;
        }
    }


    // 설정된 제출 마감 시각에 미제출 참여자 타임아웃 처리
    @Scheduled(cron = "#{@battleSchedule.submissionDeadlineCron()}", zone = "Asia/Seoul")
    public void timeoutBattle() {

        LocalDate today = LocalDate.now(clock);

        Long battleId = dailyBattleRepository.findIdByBattleDate(today)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "today_battle_not_exist"
                ));

        dailyBattleService.timeoutInProgress(battleId);
    }

}
