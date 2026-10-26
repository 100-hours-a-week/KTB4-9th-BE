package com.cosmos.cosmos_backend.dailyBattle.metrics;

import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

// 부하테스트를 위한 메트릭
@Component
@RequiredArgsConstructor
public class BattleQueueMetricsCollector {

    private final DailyBattleRepository dailyBattleRepository;
    private final BattleParticipationRepository battleParticipationRepository;
    private final BattleMetrics battleMetrics;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${battle.metrics.queue-sample-interval-ms:500}",
            initialDelayString = "${battle.metrics.queue-sample-initial-delay-ms:500}"
    )
    public void sampleQueueDepths() {
        Map<ParticipationStatus, Long> counts = new EnumMap<>(ParticipationStatus.class);

        dailyBattleRepository.findIdByBattleDate(LocalDate.now(clock))
                .ifPresent(battleId -> battleParticipationRepository.countTasksByStatus(battleId)
                        .forEach(row -> counts.put(row.getStatus(), row.getTaskCount())));

        battleMetrics.updateQueueDepths(counts);
    }
}
