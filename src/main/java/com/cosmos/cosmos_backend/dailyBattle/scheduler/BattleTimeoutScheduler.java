package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.DailyBattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;


@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class BattleTimeoutScheduler {

    private final DailyBattleService dailyBattleService;

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleSchedule battleSchedule;

    private final Clock clock;

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
            timeoutBattle();
        }

    }

}
