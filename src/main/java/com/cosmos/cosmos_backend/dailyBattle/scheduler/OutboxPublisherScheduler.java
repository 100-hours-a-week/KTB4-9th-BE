package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.dailyBattle.service.OutboxStatusChangeService;
import com.cosmos.cosmos_backend.dailyBattle.service.OutboxPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxStatusChangeService outboxPreemptService;

    private final OutboxPublishService outboxPublishService;

    private final TaskScheduler publishTaskScheduler;

    private ScheduledFuture<?> publisherTask;

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


}
