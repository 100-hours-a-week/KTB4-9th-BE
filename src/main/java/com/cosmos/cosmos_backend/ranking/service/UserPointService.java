package com.cosmos.cosmos_backend.ranking.service;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserCategoryPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserDifficultyPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import com.cosmos.cosmos_backend.ranking.repository.UserCategoryPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserDifficultyPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserPointRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPointService {

    private final UserPointRepository userPointRepository;
    private final UserDifficultyPointRepository userDifficultyPointRepository;
    private final UserCategoryPointRepository userCategoryPointRepository;
    private final UserRepository userRepository;

    @Transactional
    public void updatePoint(
            Long userId,
            Difficulty difficulty,
            Category category
    ) {

        // 0. User 조회
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 정답 →
        // 1. UserPoint 조회
        UserPoint userPoint = userPointRepository.findByUser_Id(userId).orElseThrow(() -> new IllegalArgumentException("사용자 포인트 정보를 찾을 수 없습니다."));

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
            case LV1:
                earnedPoint = 5L;
                break;
            case LV2:
                earnedPoint = 10L;
                break;
            case LV3:
                earnedPoint = 15L;
                break;
            case LV4:
                earnedPoint = 20L;
                break;
            case LV5:
                earnedPoint = 25L;
                break;
            default:
                earnedPoint = 0L;
                break;
        }


        Long streakBonus = 0L;

        // 4-1. 오늘 최초 정답인 경우에만 streak 보너스 지급
        if (firstCorrectToday) {
            streakBonus = Math.min(userPoint.getCurrentStreakDay(), 7L) * 2;
        }

        // 전체 랭킹에는 streak 보너스 추가
        Long totalEarnedPoint = earnedPoint + streakBonus;

        // 4-2. 마지막 정답 날짜 갱신
        userPoint.updateLastCorrectDate(today);

        // 5. 전체 포인트 업데이트
        userPoint.increaseTotalPoint(totalEarnedPoint);

        // 6. 해당 난이도 UserDifficultyPoint 업데이트
        Optional<UserDifficultyPoint> userDifficultyPoint = userDifficultyPointRepository.findByUser_IdAndDifficulty(userId, difficulty);

        if (userDifficultyPoint.isPresent()) {
            UserDifficultyPoint difficultyPoint = userDifficultyPoint.get();
            difficultyPoint.increasePoint(earnedPoint);
            difficultyPoint.increaseCorrectProblemCount();
        } else {
            UserDifficultyPoint difficultyPoint = new UserDifficultyPoint(
                    user,
                    difficulty,
                    earnedPoint,
                    1L
            );

            userDifficultyPointRepository.save(difficultyPoint);

            difficultyPoint.increaseCorrectProblemCount();
        }


        // 7. 해당 카테고리 UserCategoryPoint 업데이트
        Optional<UserCategoryPoint> userCategoryPoint = userCategoryPointRepository.findByUser_IdAndCategory(userId, category);

        if (userCategoryPoint.isPresent()) {
            UserCategoryPoint categoryPoint = userCategoryPoint.get();
            categoryPoint.increasePoint(earnedPoint);
            categoryPoint.increaseCorrectProblemCount();
        } else {
            UserCategoryPoint categoryPoint = new UserCategoryPoint(
                    user,
                    category,
                    earnedPoint,
                    1L
            );

            userCategoryPointRepository.save(categoryPoint);

            categoryPoint.increaseCorrectProblemCount();
        }

    }
}
