package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.BattleResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class BattleResultScheduler {

    private final BattleResultService battleResultService;

    private final BattleParticipationRepository battleParticipationRepository;

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleSchedule battleSchedule;

    private final TaskScheduler taskScheduler;

    private final Clock clock;

    private ScheduledFuture<?> recheckTask;

    // 설정된 결과 공개 시각에 실행할 결과 확정 메소드
    @Scheduled(cron = "#{@battleSchedule.resultCron()}", zone = "Asia/Seoul")
    public synchronized void finalizeBattle() {

        LocalDate today = LocalDate.now(clock);

        Long todayBattleId = dailyBattleRepository.findIdByBattleDate(today).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "today_battle_not_exist"
        ));

        // 이미 결과가 공개된 배틀이라면 중복 처리 방지
        if (!dailyBattleRepository.existsByBattleDateAndResultPublishedAtIsNull(today)) {

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

        // 4. 결과 공개 시각이 지났다면 결과 확정 재시도
        if (!now.isBefore(battleSchedule.resultTime())) {
            finalizeBattle();
        }
    }

}
