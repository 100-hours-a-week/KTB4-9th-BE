package com.cosmos.cosmos_backend.dailyBattle.consumer.service;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingConsumerPreemptResult;
import com.cosmos.cosmos_backend.dailyBattle.consumer.domain.GradingPreemptStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.worker",
        name = "enabled",
        havingValue = "true"
)
public class GradingPreemptService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final Clock clock;

    // payload 안의 participationId로 채점 대상 선점
    @Transactional
    public GradingConsumerPreemptResult preemptGradingTask(Long participationId) {

        Optional<BattleParticipation> preemptValidParticipation = battleParticipationRepository.findByIdForUpdate(participationId);

        // 참여행 없다면 유효하지 않은 상태로 전달
        if(preemptValidParticipation.isEmpty()){
            return new GradingConsumerPreemptResult(
                    participationId,
                    null,
                    GradingPreemptStatus.INVALID_STATUS
            );
        }

        // 참여 상태 확인
        BattleParticipation preemptParticipation = preemptValidParticipation.get();

        ParticipationStatus participationStatus = preemptParticipation.getParticipationStatus();

        // 현재 시각
        Instant now = clock.instant();

        // 최초 선점인 경우 → 바로 선점
        if (participationStatus == ParticipationStatus.SUBMITTED) {
            Instant leaseExpiresAt = now.plusSeconds(2);

            UUID leaseToken = UUID.randomUUID();

            preemptParticipation.markGrading(leaseExpiresAt, leaseToken);

            return new GradingConsumerPreemptResult(participationId, leaseToken, GradingPreemptStatus.PREEMPTED);
        }

        // 이미 채점중
        if (participationStatus == ParticipationStatus.GRADING) {
            // 임대 시간 유효 → 재시도 DLX
            // 재시도 큐 대상 (Retry exchage + TTL 2초)
            if(preemptParticipation.getLeaseExpiresAt().isAfter(now)) {
                return new GradingConsumerPreemptResult(participationId, null, GradingPreemptStatus.LEASE_ACTIVE);
            }

            // 임대 시간 만료 → 횟수 유효 : 즉시 재선점, 횟수 초과 : → DB 상태를 GRADING_FAILED로 변경하고 DLQ
            if (preemptParticipation.getLeaseRetryCount() >= 3) {

                preemptParticipation.markGradingFailed();
                // DLQ 대상
                return new GradingConsumerPreemptResult(participationId, null, GradingPreemptStatus.OVER_LEASE_COUNT);

            } else {
                Instant leaseExpiresAt = now.plusSeconds(2);

                UUID leaseToken = UUID.randomUUID();

                preemptParticipation.markGrading(leaseExpiresAt, leaseToken);
                // 재선점 횟수 증가
                preemptParticipation.increaseLeaseRetryCount();

                return new GradingConsumerPreemptResult(participationId, leaseToken, GradingPreemptStatus.PREEMPTED);
            }
        }

        // 이미 채점 완료 → 이미 처리된 중복 메시지이므로 ACK
        if (participationStatus.equals(ParticipationStatus.GRADING_COMPLETED)) {
            return new GradingConsumerPreemptResult(participationId, null, GradingPreemptStatus.ALREADY_COMPLETED);
        }

        // 이미 채점 실패 → 이미 실패 처리된 중복 메시지이므로 ACK
        if (participationStatus.equals(ParticipationStatus.GRADING_FAILED)) {
            return new GradingConsumerPreemptResult(participationId, null, GradingPreemptStatus.ALREADY_FAILED);
        }


        return new GradingConsumerPreemptResult(participationId, null, GradingPreemptStatus.INVALID_STATUS);

    }

}