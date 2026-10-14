package com.cosmos.cosmos_backend.dailyBattle.worker;

import jakarta.persistence.Column;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BattleGradingWorker {

    // 1초에 2번, 작업과 겹치면 작업 끝나고 다음거 가져오게
    @Scheduled(fixedDelay = 500)
    public void processBattleSubmissions() {

    }
}
