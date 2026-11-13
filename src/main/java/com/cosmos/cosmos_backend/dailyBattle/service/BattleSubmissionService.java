package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionInfo;
import com.cosmos.cosmos_backend.dailyBattle.dto.request.DailyBattleSubmissionRequestDto;
import com.cosmos.cosmos_backend.dailyBattle.dto.response.BattleSubmissionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BattleSubmissionService {

    private final BattleSubmissionCacheService battleSubmissionCacheService;
    private final DailyBattleService dailyBattleService;

    public BattleSubmissionResponseDto submitBattleAnswers(
            DailyBattleSubmissionRequestDto submissionRequest,
            Long battleId,
            Long userId,
            Instant submittedAt
    ) {

        // 트랜잭션 시작 전에 공통 배틀 제출 정보를 캐시에서 조회
        BattleSubmissionInfo submissionInfo = battleSubmissionCacheService.getBattleSubmissionInfo(battleId);

        return dailyBattleService.battleSubmission(
                submissionRequest,
                battleId,
                userId,
                submittedAt,
                submissionInfo
        );
    }
}
