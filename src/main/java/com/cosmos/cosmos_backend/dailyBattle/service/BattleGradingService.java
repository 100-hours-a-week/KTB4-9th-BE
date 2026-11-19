package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.GradingPreempt;
import com.cosmos.cosmos_backend.dailyBattle.domain.GradingResult;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.*;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BattleGradingService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    private final BattleCaseRepository battleCaseRepository;

    private final BattleResultSaveService battleResultSaveService;

    private final Clock clock;


    // 작업 선점 트랜잭션
    @Transactional
    public Optional<GradingPreempt> preemptSubmission(Long battleId) {

        // 1. 작업 조회 + 락 획득
        Optional<BattleParticipation> submissionTask = battleParticipationRepository.findSubmittedForUpdate(battleId);

        // 2. 작업이 없으면 종료
        if (submissionTask.isEmpty()) {
            return Optional.empty();
        }

        BattleParticipation preemptedParticipation = submissionTask.get();

        // 2. 재선점 대상일 때만 횟수 검사
        if (preemptedParticipation.getParticipationStatus() == ParticipationStatus.GRADING) {

            // 3. 재선점 한도에 도달했다면 최종 실패 처리
            if (preemptedParticipation.getLeaseRetryCount() >= 3) {
                preemptedParticipation.markGradingFailed();
                return Optional.empty();
            }

            // 4. 실제 재선점 횟수 증가
            preemptedParticipation.increaseLeaseRetryCount();
        }

        // 5. 채점 중으로 상태 변경
        Instant leaseExpiresAt = clock.instant().plusSeconds(2);

        UUID leaseToken = UUID.randomUUID();

        preemptedParticipation.markGrading(leaseExpiresAt, leaseToken);

        // 6. 참여 작업 정보 반환
        GradingPreempt preemptTask = new GradingPreempt(
                submissionTask.get().getParticipationId(),
                leaseToken
        );

        return Optional.of(preemptTask);

    }

    // 채점 메소드
    public void battleGrade(Long participationId, UUID leaseToken) {

        // 유저 제출 정보 가져오기
        List<BattleSubmission> userSubmissions = battleSubmissionRepository.findByParticipation_participationId(participationId);

        // TODO : 이부분 캐시로 빼도 괜찮을듯
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

        // 정답 확인 후 결과 담기
        List<GradingResult> gradingResults = new ArrayList<>();

        for(BattleSubmission battleSubmission : userSubmissions){
            BattleCase battleCase = answerMap.get(battleSubmission.getBattleCase().getCaseId());

            // 정답 데이터가 없으면 채점 불가능
            if (battleCase == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Battle_Case_not_found");
            }

            GradingResult gradingResult;

            if(Objects.equals(battleCase.getExpectedOutput(), battleSubmission.getSubmittedOutput())){
                gradingResult = new GradingResult(battleSubmission.getBattleSubmissionId(), true);
                gradingResults.add(gradingResult);
            } else {
                gradingResult = new GradingResult(battleSubmission.getBattleSubmissionId(), false);
                gradingResults.add(gradingResult);
            }
        }

        // 채점 결과 저장
        BattleParticipation battleParticipation = battleParticipationRepository.findById(participationId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "Battle_Participation_not_found"
        ));

        battleResultSaveService.saveGradedResult(
                battleParticipation.getParticipationId(),
                leaseToken,
                gradingResults
        );
    }

    // 채점 재시도 횟수 증가
    @Transactional
    public void checkGradingFailed(Long participationId, UUID leaseToken) {

        BattleParticipation battleParticipation = battleParticipationRepository.findByIdForUpdate(participationId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Participation_not_found")
        );

        //현재 선점자와 처리 상태 검증
        if (!Objects.equals(battleParticipation.getLeaseToken(), leaseToken)
                || battleParticipation.getParticipationStatus() != ParticipationStatus.GRADING) {
            return;
        }

        // 재시도 횟수 증가
        battleParticipation.increaseRetryCount();
        // 재시도 가능 여부 확인
        if (battleParticipation.getRetryCount() < 3) {
            // 제출 처리로 되돌리기 (다시 worker가 선점하도록)
            battleParticipation.markReSubmit();
        } else {
            // 재시도 횟수 초과 시 채점 실패 처리
            battleParticipation.markGradingFailed();
        }
    }

    // 채점 실패 처리
    @Transactional
    public void markGradingFailed(Long participationId) {
        BattleParticipation battleParticipation = battleParticipationRepository.findById(participationId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Battle_Participation_not_found")
        );

        battleParticipation.markGradingFailed();
    }
}
