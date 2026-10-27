package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.GradingPreempt;
import com.cosmos.cosmos_backend.dailyBattle.metrics.BattleMetrics;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BattleGradingService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    private final BattleCaseRepository battleCaseRepository;

    // 부하테스트를 위한 메트릭
    private final BattleMetrics battleMetrics;

    // 부하테스트를 위한 메트릭
    private final Clock clock;


    // 작업 선점 트랜잭션
    @Transactional
    public Optional<GradingPreempt> preemptSubmission(){

        // 1. 작업 조회 + 락 획득
        Optional<BattleParticipation> submissionTask = battleParticipationRepository.findSubmittedForUpdate();

        // 2. 작업이 없으면 종료
        if (submissionTask.isEmpty()) {
            return Optional.empty();
        }

        // 3. 채점 중으로 상태 변경
        LocalDateTime leaseTime = LocalDateTime.now(clock).plusSeconds(2);

        UUID leaseToken = UUID.randomUUID();

        BattleParticipation preemptedParticipation = submissionTask.get();

        preemptedParticipation.markGrading(leaseTime, leaseToken);

        // 부하테스트를 위한 메트릭
        battleMetrics.recordClaim(preemptedParticipation.getSubmittedAt(), clock.instant());

        // 4. 참여 작업 정보 반환
        GradingPreempt preemptTask = new GradingPreempt(
                submissionTask.get().getParticipationId(),
                leaseToken
        );

        return Optional.of(preemptTask);

    }

    // 채점 메소드
    @Transactional
    public void battleGrade(
            Long participationId,
            UUID leaseToken,
            // 부하테스트를 위한 메트릭
            Instant processingStartedAt
    ) {

        // 유저 제출 정보 가져오기
        List<BattleSubmission> userSubmissions = battleSubmissionRepository.findByParticipation_participationId(participationId);

        // 제출 정보에서 BattleCase id 리스트 추출
        List<Long> caseIds = new ArrayList<>();

        for (BattleSubmission battleSubmission : userSubmissions) {
            caseIds.add(battleSubmission.getBattleCase().getCaseId());
        }

        // BattleCase 리스트로 한 번에 조회
        List<BattleCase> battleCases = battleCaseRepository.findAllByCaseIds(caseIds);

        // 정답 map에 담기
        Map<Long, BattleCase> answerMap =  new HashMap<>();

        for(BattleCase battleCase : battleCases){
            answerMap.put(battleCase.getCaseId(), battleCase);
        }

        // 정답 확인
        Boolean allAnswersCorrect = true;

        for(BattleSubmission battleSubmission : userSubmissions){
            BattleCase battleCase = answerMap.get(battleSubmission.getBattleCase().getCaseId());

            // 정답 데이터가 없으면 채점 불가능
            if (battleCase == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Battle_Case_not_found");
            }

            if(Objects.equals(battleCase.getExpectedOutput(), battleSubmission.getSubmittedOutput())){
                battleSubmission.answerCorrect();
            } else {
                battleSubmission.answerIncorrect();
                // 1개라도 오답이면 전체 정답 오답처리
                allAnswersCorrect = false;

            }
        }

        // 참여 상태 변경, 전체 정답/오답처리
        BattleParticipation battleParticipation = battleParticipationRepository.findById(participationId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "Battle_Participation_not_found"
        ));

        if(allAnswersCorrect){
            battleParticipation.isCorrect();
        } else {
            battleParticipation.isWrong();
        }

        battleParticipation.markEvaluatingComplete();

        // 부하테스트를 위한 메트릭
        Instant completedAt = clock.instant();
        battleMetrics.recordProcessing(
                Duration.between(processingStartedAt, completedAt),
                "success"
        );
        battleMetrics.recordCompleted(battleParticipation.getSubmittedAt(), completedAt);
    }

    // 채점 재시도 횟수 증가
    @Transactional
    public void checkGradingFailed(Long participationId) {

        BattleParticipation battleParticipation = battleParticipationRepository.findById(participationId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Participation_not_found")
        );

        // 재시도 횟수 증가
        battleParticipation.increaseRetryCount();
        // 부하테스트를 위한 메트릭
        battleMetrics.recordRetry();

        Integer retryCount = battleParticipation.getRetryCount();

        // 재시도 가능 여부 확인
        if (retryCount < 3) {
            // 제출 처리로 되돌리기 (다시 worker가 선점하도록)
            battleParticipation.markReSubmit();
        } else {
            // 재시도 횟수 초과 시 채점 실패 처리
            battleParticipation.markGradingFailed();
            // 부하테스트를 위한 메트릭
            battleMetrics.recordFailure();
        }
    }

    // 채점 실패 처리
    @Transactional
    public void markGradingFailed(Long participationId) {
        BattleParticipation battleParticipation = battleParticipationRepository.findById(participationId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Participation_not_found")
        );

        battleParticipation.markGradingFailed();
        // 부하테스트를 위한 메트릭
        battleMetrics.recordFailure();
    }
}
