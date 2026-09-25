package com.cosmos.cosmos_backend.ranking.service;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import com.cosmos.cosmos_backend.ranking.repository.UserCategoryPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserDifficultyPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserPointRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserPointService {

    private final UserPointRepository userPointRepository;
    private final UserDifficultyPointRepository userDifficultyPointRepository;
    private final UserCategoryPointRepository userCategoryPointRepository;

    @Transactional
    public void updatePoint(
            Long userId,
            Difficulty difficulty,
            Category category
    ) {

        // 정답 →
        // 1. UserPoint 조회
        UserPoint userPoint = userPointRepository.findByUser_Id(userId).get();

        // 2. 전체 정답 문제 수 증가
        userPoint.increaseTotalCorrectProblemCount();

        // 3. 연속 정답 일수 계산
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 오늘의 첫 정답인지 확인 (마지막 정답 날짜가 없거나, 오늘이 아니거나라면 첫정답)
        boolean firstCorrectToday = (userPoint.getLastCorrectDate() == null || !userPoint.getLastCorrectDate().equals(today));

        if (userPoint.getLastCorrectDate() == null) {
            // 첫 정답
            userPoint.resetCorrectStreak();

        } else if (userPoint.getLastCorrectDate().equals(yesterday)) {
            // 어제도 정답 → 연속 일수 +1
            userPoint.increaseCorrectStreak();

        } else if (!userPoint.getLastCorrectDate().equals(today)) {
            // 마지막 정답이 어제도 오늘도 아님 → 연속 끊김
            userPoint.resetCorrectStreak();
        }

        // 난이도별 기본 포인트 : LV1 = 5점, LV2 = 10점, LV3 = 15점, LV4 = 20점, LV5 = 25점
        // 연속 일수 보너스 = 현재 streak * 2점 → 하루에 1번만, 최대 14점
        // 4. 난이도에 따른 기본 포인트 계산
        Long earnedPoint;

        switch (difficulty) {
            case LV1 : earnedPoint = 5L; break;
            case LV2 : earnedPoint = 10L; break;
            case LV3 : earnedPoint = 15L; break;
            case LV4 : earnedPoint = 20L; break;
            case LV5 : earnedPoint = 25L; break;
            default: earnedPoint = 0L; break;
        }

        // 4-1. 오늘 최초 정답인 경우에만 streak 보너스 지급
        if (firstCorrectToday) {
            Long streakBonus = userPoint.getCurrentStreakDay() * 2;
            earnedPoint += streakBonus;
        }

        // 4-2. 마지막 정답 날짜 갱신
        userPoint.updateLastCorrectDate(today);

        // 5. 전체 포인트 업데이트
        userPoint.increaseTotalPoint(earnedPoint);

        // 6. 해당 난이도 UserDifficultyPoint 업데이트

        // 7. 해당 카테고리 UserCategoryPoint 업데이트


    }
}
