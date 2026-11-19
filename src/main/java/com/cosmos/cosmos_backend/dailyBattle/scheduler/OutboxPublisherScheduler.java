package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.service.OutboxStatusChangeService;
import com.cosmos.cosmos_backend.dailyBattle.service.OutboxPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class OutboxPublisherScheduler {

    private final Clock clock;

    private final OutboxPublishService outboxPublishService;

    private final TaskScheduler publishTaskScheduler;

    private ScheduledFuture<?> publisherTask;

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleSchedule battleSchedule;

    @Scheduled(cron = "#{@battleSchedule.openCron()}", zone = "Asia/Seoul")
    public synchronized void outboxPublisher(){

        // 이미 실행 중이면 중복 등록 방지
        if (publisherTask != null && !publisherTask.isDone()) {
            return;
        }

        // 500ms 마다 배치 폴링 + 이벤트 발행
        publisherTask = publishTaskScheduler.scheduleWithFixedDelay(
                outboxPublishService::eventPublish,
                Duration.ofMillis(500)
        );


    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoveryOutboxPublisher(){

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

        // 결과 공개 전까지는 publisher 복구
        outboxPublisher();

    }


}
