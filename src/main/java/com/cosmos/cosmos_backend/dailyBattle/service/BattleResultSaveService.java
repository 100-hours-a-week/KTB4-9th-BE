package com.cosmos.cosmos_backend.dailyBattle.service;


import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.GradingResult;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BattleResultSaveService {

    private final BattleParticipationRepository battleParticipationRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    // 채점 결과 저장 메소드
    @Transactional
    public boolean saveGradedResult(
            Long participationId,
            UUID leaseToken,
            List<GradingResult> gradingResults
    ){

        // 해당 참여자 행에 대한 락 획득
        BattleParticipation battleParticipation = battleParticipationRepository.findByIdForUpdate(participationId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "Participation_Not_Found")
        );

        // 임대 토큰, 참여 상태 검증
        if(!Objects.equals(battleParticipation.getLeaseToken(), leaseToken)
                || battleParticipation.getParticipationStatus() != ParticipationStatus.GRADING){
            return false;
        }

        Boolean allCorrect = true;

        // 개별 정답 기록
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

        // 전체 정답 여부 기록
        if(allCorrect){
            battleParticipation.isCorrect();
        } else {
            battleParticipation.isWrong();
        }

        battleParticipation.markEvaluatingComplete();

        return true;
    }

}
