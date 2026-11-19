package com.cosmos.cosmos_backend.dailyBattle.service;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.dailyBattle.domain.BattleSubmissionInfo;
import com.cosmos.cosmos_backend.dailyBattle.projection.BattleBasicInfoProjection;
import com.cosmos.cosmos_backend.dailyBattle.projection.BattleCaseIdProjection;
import com.cosmos.cosmos_backend.dailyBattle.repository.BattleCaseRepository;
import com.cosmos.cosmos_backend.dailyBattle.repository.DailyBattleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BattleSubmissionCacheService {

    private final DailyBattleRepository dailyBattleRepository;

    private final BattleCaseRepository battleCaseRepository;

    // 키 : 배틀 Id
    @Cacheable(
            cacheManager = "battleSubmissionCacheManager",
            cacheNames = "battleSubmissionInfo",
            key = "#battleId",
            sync = true
    )
    // 제출 요청 api에 반복되는 배틀 날짜, 케이스id 조회 로컬 캐싱
    public BattleSubmissionInfo getBattleSubmissionInfo(Long battleId) {

        // 배틀 기본 정보만 조회
        BattleBasicInfoProjection battleInfo = dailyBattleRepository.findBasicInfoByBattleId(battleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Battle_not_found"
                ));

        // 배틀 케이스 기본 정보만 조회
        List<BattleCaseIdProjection> battleCases = battleCaseRepository.findCaseIdsByBattleId(battleId);

        if (battleCases.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Battle_case_not_found");
        }


        Map<Integer, Long> mutableCaseIdByCaseNum = new HashMap<>();

        for (BattleCaseIdProjection battleCase : battleCases) {

            Integer caseNum = battleCase.getDisplayOrder();
            Long caseId = battleCase.getCaseId();

            mutableCaseIdByCaseNum.put(caseNum, caseId);
        }


        // 캐시된 값 외부에서 변경 안 되도록.. 불변 map으로 변환
        Map<Integer, Long> caseIdByCaseNum = Map.copyOf(mutableCaseIdByCaseNum);


        return new BattleSubmissionInfo(
                battleInfo.getBattleId(),
                battleInfo.getBattleDate(),
                caseIdByCaseNum
        );
    }
}
