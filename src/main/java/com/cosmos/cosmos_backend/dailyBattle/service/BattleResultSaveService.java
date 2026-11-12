package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.GradingResult;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleMetrics;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BattleResultSaveService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    // 부하테스트를 위한 메트릭
    private final BattleMetrics battleMetrics;

    // 부하테스트를 위한 메트릭
    private final EntityManager entityManager;

    // 채점 결과 저장 메소드
    @Transactional
    public boolean saveGradedResult(
            Long participationId,
            UUID leaseToken,
            List<GradingResult> gradingResults
    ){

        // 해당 참여자 행에 대한 락 획득
        // 부하테스트를 위한 메트릭
        long phaseStartedAt = System.nanoTime();
        BattleParticipation battleParticipation;
        try {
            battleParticipation = battleParticipationRepository.findByIdForUpdate(participationId).orElseThrow(
                    () -> new BusinessException(HttpStatus.NOT_FOUND, "Participation_Not_Found")
            );
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "participation_lock",
                    "success"
            );
        } catch (RuntimeException exception) {
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "participation_lock",
                    "error"
            );
            throw exception;
        }

        // 임대 토큰, 참여 상태 검증
        if(!Objects.equals(battleParticipation.getLeaseToken(), leaseToken)
                || battleParticipation.getParticipationStatus() != ParticipationStatus.GRADING){
            return false;
        }

        Boolean allCorrect = true;

        // 개별 정답 기록
        // 부하테스트를 위한 메트릭
        phaseStartedAt = System.nanoTime();
        try {
            for(GradingResult gradingResult : gradingResults){
                // 제출 id 있는지 확인
                BattleSubmission battleSubmission = battleSubmissionRepository.findById(gradingResult.submissionId()).orElseThrow(
                        () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Submission_not_found")
                );

                if(gradingResult.correct()){
                    battleSubmission.answerCorrect();
                }else {
                    battleSubmission.answerIncorrect();
                    allCorrect = false;
                }
            }
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "submission_lookup",
                    "success"
            );
        } catch (RuntimeException exception) {
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "submission_lookup",
                    "error"
            );
            throw exception;
        }

        // 전체 정답 여부 기록
        if(allCorrect){
            battleParticipation.isCorrect();
        } else {
            battleParticipation.isWrong();
        }

        battleParticipation.markEvaluatingComplete();

        // 부하테스트를 위한 메트릭
        registerCommitMetric();

        // 부하테스트를 위한 메트릭
        phaseStartedAt = System.nanoTime();
        try {
            // Dirty Checking UPDATE를 커밋과 분리해서 측정하기 위해 트랜잭션 안에서 명시적으로 flush
            entityManager.flush();
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "result_flush",
                    "success"
            );
        } catch (RuntimeException exception) {
            battleMetrics.recordGradingResultDbPhase(
                    Duration.ofNanos(System.nanoTime() - phaseStartedAt),
                    "result_flush",
                    "error"
            );
            throw exception;
        }

        return true;
    }

    // 부하테스트를 위한 메트릭
    private void registerCommitMetric() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private long commitStartedAt = -1L;

            @Override
            public void beforeCommit(boolean readOnly) {
                commitStartedAt = System.nanoTime();
            }

            @Override
            public void afterCompletion(int status) {
                if (commitStartedAt < 0L) {
                    return;
                }

                battleMetrics.recordGradingResultDbPhase(
                        Duration.ofNanos(System.nanoTime() - commitStartedAt),
                        "transaction_commit",
                        status == TransactionSynchronization.STATUS_COMMITTED ? "success" : "error"
                );
            }
        });
    }

}
