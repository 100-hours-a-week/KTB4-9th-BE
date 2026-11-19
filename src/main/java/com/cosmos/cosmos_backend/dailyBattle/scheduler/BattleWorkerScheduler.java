package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import com.cosmos.cosmos_backend.dailyBattle.worker.BattleGradingWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.legacy-db-worker",
        name = "enabled",
        havingValue = "true"
)
public class BattleWorkerScheduler {

    private final BattleResultService battleResultService;

    private final DailyBattleService dailyBattleService;

    private final BattleParticipationRepository battleParticipationRepository;

    private final DailyBattleRepository dailyBattleRepository;

    private final Clock clock;

    private final TaskScheduler taskScheduler;

    private final BattleGradingWorker battleGradingWorker;

    private final BattleSchedule battleSchedule;

    private List<ScheduledFuture<?>> workerTasks = new ArrayList<>();

    private ScheduledFuture<?> recheckTask;

    private final BattleTimeoutScheduler battleTimeoutScheduler;

    // 설정된 배틀 시작 시각에 채점 워커 시작
    @Scheduled(cron = "#{@battleSchedule.openCron()}", zone = "Asia/Seoul")
    public synchronized void startGradingWorker(){

        // 종료되거나 취소된 작업은 실행 중인 워커 수에서 제외
        workerTasks.removeIf(task -> task.isCancelled() || task.isDone());

        // 이미 워커가 실행 중이면 종료
        if(workerTasks.size() >= 2){
            return;
        }

        LocalDate today = LocalDate.now(clock);
        Long battleId = dailyBattleRepository.findIdByBattleDate(today)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "today_battle_not_exist"
                ));

        //필요한 워커 수 계산 (최대 2개)
        int neededWorkers = 2 - workerTasks.size();

        // worker 작업 등록 (필요한 개수 반영)
        for (int i = 0; i < neededWorkers; i++) {

            ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(
                    () -> battleGradingWorker.gradingBattleSubmissions(battleId),
                    Duration.ofMillis(100)
            );

            workerTasks.add(task);
        }

    }

    // worker 종료
    public synchronized void stopGradingWorker() {

        for (ScheduledFuture<?> task : workerTasks) {
            task.cancel(false);
        }

        workerTasks.clear();
    }

    // 설정된 결과 공개 시각에 실행할 결과 확정 메소드
    @Scheduled(cron = "#{@battleSchedule.resultCron()}", zone = "Asia/Seoul")
    public synchronized void finalizeBattle() {

        LocalDate today = LocalDate.now(clock);

        Long todayBattleId = dailyBattleRepository.findIdByBattleDate(today).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "today_battle_not_exist"
        ));

        // 이미 결과가 공개된 배틀이라면 중복 처리 방지
        if (!dailyBattleRepository.existsByBattleDateAndResultPublishedAtIsNull(today)) {
            stopGradingWorker();

            if (recheckTask != null) {
                recheckTask.cancel(false);
                recheckTask = null;
            }

            return;
        }

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



    //서버 다운시 복구 로직
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {

        // 1. 현재 날짜와 시간
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        // 2. 오늘 배틀이 없거나 이미 결과가 공개됐다면 종료
        boolean isUnpublishedBattle = dailyBattleRepository.existsByBattleDateAndResultPublishedAtIsNull(today);

        if (!isUnpublishedBattle) {
            return;
        }

        // 3. 배틀 시작 전이라면 복구할 필요 없음
        if (now.isBefore(battleSchedule.openTime())) {
            return;
        }

        // 4. 제출 마감 시각이 지났다면 타임아웃 복구
        if (!now.isBefore(battleSchedule.submissionDeadline())) {
            battleTimeoutScheduler.timeoutBattle();
        }

        // 5. 채점 워커 복구
        startGradingWorker();

        // 6. 결과 공개 시각이 지났다면 결과 확정 재시도
        if (!now.isBefore(battleSchedule.resultTime())) {
            finalizeBattle();
        }
    }

}
