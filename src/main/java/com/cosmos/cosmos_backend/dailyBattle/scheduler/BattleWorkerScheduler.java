package com.cosmos.cosmos_backend.dailyBattle.scheduler;

import com.cosmos.cosmos_backend.dailyBattle.worker.BattleGradingWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class BattleWorkerScheduler {

    private final TaskScheduler taskScheduler;

    private final BattleGradingWorker battleGradingWorker;

    private List<ScheduledFuture<?>> workerTasks = new ArrayList<>();

    // 매일 12:00에 채점 워커 시작
    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
    public void startGradingWorker(){

        // worker에 작업 2개 등록
        for (int i = 0; i < 2; i++) {

            ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(
                    battleGradingWorker::gradingBattleSubmissions,
                    Duration.ofMillis(100)
            );

            workerTasks.add(task);
        }
    }
}
