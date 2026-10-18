package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleCase;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BattleGradingService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    private final BattleCaseRepository battleCaseRepository;


    // 작업 선점 트랜잭션
    @Transactional
    public Optional<Long> preemptSubmission(){

        // 1. 작업 조회 + 락 획득
        Optional<BattleParticipation> optional = battleParticipationRepository.findSubmittedForUpdate();

        // 2. 작업이 없으면 종료
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        // 3. 채점 중으로 상태 변경
        BattleParticipation preemptedParticipation = optional.get();
        preemptedParticipation.markGrading();

        // 4. 참여 id 반환
        return Optional.of(preemptedParticipation.getParticipationId());

    }

    // 채점 메소드
    @Transactional
    public void battleGrade(Long participationId) {

        // 유저 제출 정보 가져오기
        List<BattleSubmission> userSubmissions = battleSubmissionRepository.findByBattleParticipation_participationId(participationId);

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
