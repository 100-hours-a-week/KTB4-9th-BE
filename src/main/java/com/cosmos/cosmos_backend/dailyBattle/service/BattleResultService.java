package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleParticipation;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.BattleSubmission;
import com.cosmos.cosmos_backend.dailyBattle.domain.entity.DailyBattle;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleResultResponseDto;
import com.cosmos.cosmos_backend.dailyBattle.projection.MyBattleRankingProjection;
import com.cosmos.cosmos_backend.dailyBattle.projection.TopRankingProjection;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleParticipationRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleSubmissionRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import com.cosmos.cosmos_backend.dailyBattle.scheduler.BattleWorkerScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BattleResultService {

    private final BattleWorkerScheduler battleWorkerScheduler;

    private final BattleParticipationRepository battleParticipationRepository;

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleSubmissionRepository battleSubmissionRepository;

    // 결과 공개 가능 처리
    @Transactional
    public void battleResultPublished(Long battleId, LocalDateTime publishedAt) {
        DailyBattle dailyBattle = dailyBattleRepository.findById(battleId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "Battle_not_found."
        ));

        dailyBattle.publishResult(publishedAt);

    }


    // 배틀 결과 조회
    @Transactional(readOnly = true)
    public BattleResultResponseDto getBattleResult(Long battleId, Long userId) {

        // 1. 배틀 조회
        DailyBattle dailyBattle = dailyBattleRepository.findById(battleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "Battle_not_found."
                ));

        // 2. 결과 공개 여부 확인
        if (dailyBattle.getResultPublishedAt() == null) {
            return null;
        }

        // 3. 내 참여 정보 조회
        BattleParticipation myParticipation = battleParticipationRepository.findByDailyBattleIdAndUser_Id(battleId, userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "User_participation_not_found."
                ));

        ParticipationStatus status = myParticipation.getParticipationStatus();

        // 4. 내 결과 초기값
        Long myRank = null;
        Long elapsedSeconds = null;
        List<BattleResultResponseDto.MyResult.MyAnswer> myAnswers = null;

        // 5. 채점 완료 상태라면 상세 결과 조회
        if (status == ParticipationStatus.GRADING_COMPLETED) {

            // 5-1. 내 순위 조회 (정답자만 순위 존재)
            MyBattleRankingProjection myBattleRanking =
                    battleParticipationRepository
                            .findMyBattleRanking(battleId, userId)
                            .orElse(null);

            if (myBattleRanking != null) {
                myRank = myBattleRanking.getRank();
            }

            // 5-2. 소요 시간 계산 (오답자도 포함)
            if (myParticipation.getStartedAt() != null
                    && myParticipation.getSubmittedAt() != null) {

                elapsedSeconds = ChronoUnit.SECONDS.between(
                        myParticipation.getStartedAt(),
                        myParticipation.getSubmittedAt()
                );
            }

            // 5-3. 내 제출 답안 조회
            List<BattleSubmission> mySubmissions = battleSubmissionRepository.findByBattleParticipation_participationId(myParticipation.getParticipationId());

            myAnswers = new ArrayList<>();

            for (BattleSubmission submission : mySubmissions) {

                BattleResultResponseDto.MyResult.MyAnswer answer =
                        new BattleResultResponseDto.MyResult.MyAnswer(
                                submission.getBattleCase().getCaseId(),
                                submission.getBattleCase().getDisplayOrder(),
                                submission.getBattleCase().getInput(),
                                submission.getSubmittedOutput(),
                                submission.getBattleCase().getExpectedOutput(),
                                submission.getIsCorrect()
                        );

                myAnswers.add(answer);
            }
        }

        // 6. 내 결과 DTO 생성
        BattleResultResponseDto.MyResult myResult =
                new BattleResultResponseDto.MyResult(
                        status,
                        myRank,
                        elapsedSeconds,
                        myParticipation.getAllCorrect(),
                        myAnswers
                );

        // 7. top5 랭킹 조회
        List<TopRankingProjection> top5Rankings = battleParticipationRepository.findTop5Rankings(battleId);

        List<BattleResultResponseDto.TopRanking> top5Response = new ArrayList<>();

        for (TopRankingProjection projection : top5Rankings) {

            BattleResultResponseDto.TopRanking ranking =
                    new BattleResultResponseDto.TopRanking(
                            projection.getRank(),
                            projection.getUserId(),
                            projection.getUserName(),
                            projection.getProfileImageUrl(),
                            projection.getElapsedMicros() / 1_000_000
                    );

            top5Response.add(ranking);
        }

        // 8. 최종 응답 조립
        return new BattleResultResponseDto(
                battleId,
                dailyBattle.getBattleDate(),
                dailyBattle.getTitle(),
                myResult,
                top5Response
        );
    }

}
