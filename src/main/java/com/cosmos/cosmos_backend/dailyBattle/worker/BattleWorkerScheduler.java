package com.cosmos.cosmos_backend.dailyBattle.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class BattleWorkerScheduler {

    private final TaskScheduler taskScheduler;

    private final BattleGradingWorker battleGradingWorker;

    private ScheduledFuture<?> workerTask;

    // 매일 12시에 Worker 시작
    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
    public void startWorker() {

        workerTask = taskScheduler.scheduleWithFixedDelay(
                battleGradingWorker::gradingBattleSubmissions,
                Duration.ofMillis(100)
        );
    }

    // 매일 12시 10분에 Worker 중지
    @Scheduled(cron = "0 10 12 * * *", zone = "Asia/Seoul")
    public void stopWorker() {

        if (workerTask != null) {
            workerTask.cancel(false);
            workerTask = null;
        }
    }
}
