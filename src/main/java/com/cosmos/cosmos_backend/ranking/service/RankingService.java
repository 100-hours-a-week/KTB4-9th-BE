package com.cosmos.cosmos_backend.ranking.service;


import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserCategoryPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserDifficultyPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import com.cosmos.cosmos_backend.ranking.dto.GlobalRankingResponseDto;
import com.cosmos.cosmos_backend.ranking.dto.MyRankingResponseDto;
import com.cosmos.cosmos_backend.ranking.dto.RankingCommonResponseDto;
import com.cosmos.cosmos_backend.ranking.dto.RankingFilterResponseDto;
import com.cosmos.cosmos_backend.ranking.repository.UserCategoryPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserDifficultyPointRepository;
import com.cosmos.cosmos_backend.ranking.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserPointRepository userPointRepository;

    private final UserDifficultyPointRepository userDifficultyPointRepository;

    private final UserCategoryPointRepository userCategoryPointRepository;

    private final UserRepository userRepository;

    // 전체 랭킹 조회
    public GlobalRankingResponseDto getRanking(Long userId) {

        Optional<UserPoint> userPoint = userPointRepository.findByUser_Id(userId);

        // 0. 유저 정보 조회
        User user = userRepository.findById(userId).get();

        // 1. 유저 개인 포인트 계산
        Long myPoint = userPoint.get().getTotalPoint();

        // 2. 유저 랭킹 계산
        Long myRank = userPointRepository.countByTotalPointGreaterThan(myPoint) + 1;

        // 3. TOP 100 계
        List<UserPoint> top100User =  userPointRepository.findTop100ByOrderByTotalPointDesc();

        // 4. 응답 조립
        // 4-1. 필터 응답 조립
        RankingFilterResponseDto rankingFilter = new RankingFilterResponseDto(
                null,
                null
        );

        // 4-2. 내 랭킹 응답 조립
        MyRankingResponseDto myRanking = new MyRankingResponseDto(
                userId,
                user.getUsername(),
                user.getProfileImageUrl(),
                myRank,
                myPoint
        );

        // 4-3. Top 100 응답 조립
        List<RankingCommonResponseDto> top100rankings = new ArrayList<>();

        Long previousRanking = 0L;

        for(int i = 0 ; i < top100User.size() ; i++){
            Long rankings;

            if (i > 0 && top100User.get(i).getTotalPoint().equals(top100User.get(i - 1).getTotalPoint())) {
                // 동점이면 이전 사람과 같은 순위
                rankings = previousRanking;

            } else {
                // 동점이 아니면 현재 위치 + 1
                rankings = Long.valueOf(i + 1);
            }

            previousRanking = rankings;

            RankingCommonResponseDto rankingCommonResponse = new RankingCommonResponseDto(
                    top100User.get(i).getUser().getUsername(),
                    top100User.get(i).getUser().getProfileImageUrl(),
                    rankings,
                    top100User.get(i).getTotalPoint()
            );

            top100rankings.add(rankingCommonResponse);
        }

        // 5. 최종 응답 조립
        GlobalRankingResponseDto globalRanking = new GlobalRankingResponseDto(
                rankingFilter,
                myRanking,
                top100rankings
        );

        return globalRanking;
    }

    public GlobalRankingResponseDto getDifficultyRanking(Long userId, Difficulty difficulty) {

        // 0. 유저 정보 조회
        User user = userRepository.findById(userId).get();

        // 1. 해당 난이도의 유저 개인 포인트 조회
        UserDifficultyPoint userDifficultyPoint = userDifficultyPointRepository.findByUser_IdAndDifficulty(userId, difficulty).get();

        Long myPoint = userDifficultyPoint.getPoint();

        // 2. 해당 난이도의 유저 랭킹 계산
        Long myRank = userDifficultyPointRepository.countByDifficultyAndPointGreaterThan(difficulty, myPoint) + 1;

        // 3. 해당 난이도의 TOP 100 조회
        List<UserDifficultyPoint> top100User = userDifficultyPointRepository.findTop100ByDifficultyOrderByPointDesc(difficulty);

        // 4. 응답 조립
        // 4-1. 필터 응답 조립
        RankingFilterResponseDto rankingFilter = new RankingFilterResponseDto(difficulty, null);

        // 4-2. 내 랭킹 응답 조립
        MyRankingResponseDto myRanking = new MyRankingResponseDto(
                userId,
                user.getUsername(),
                user.getProfileImageUrl(),
                myRank,
                myPoint
        );

        // 4-3. TOP 100 응답 조립
        List<RankingCommonResponseDto> top100rankings = new ArrayList<>();

        Long previousRanking = 0L;

        for (int i = 0; i < top100User.size(); i++) {

            Long rankings;

            if (i > 0 && top100User.get(i).getPoint().equals(top100User.get(i - 1).getPoint())) {

                rankings = previousRanking;

            } else {
                rankings = Long.valueOf(i + 1);
            }

            previousRanking = rankings;

            RankingCommonResponseDto rankingCommonResponse =
                    new RankingCommonResponseDto(
                            top100User.get(i).getUser().getUsername(),
                            top100User.get(i).getUser().getProfileImageUrl(),
                            rankings,
                            top100User.get(i).getPoint()
                    );

            top100rankings.add(rankingCommonResponse);
        }

        // 5. 최종 응답 조립
        GlobalRankingResponseDto globalRanking = new GlobalRankingResponseDto(
                rankingFilter,
                myRanking,
                top100rankings
        );

        return globalRanking;
    }


    // 카테고리 별 랭킹 조회
    public GlobalRankingResponseDto getCategoryRanking(Long userId, Category category) {

        // 0. 유저 정보 조회
        User user = userRepository.findById(userId).get();

        // 1. 해당 카테고리의 유저 개인 포인트 조회
        UserCategoryPoint userCategoryPoint = userCategoryPointRepository.findByUser_IdAndCategory(userId, category).get();

        Long myPoint = userCategoryPoint.getPoint();

        // 2. 해당 카테고리의 유저 랭킹 계산
        Long myRank = userCategoryPointRepository.countByCategoryAndPointGreaterThan(category, myPoint) + 1;

        // 3. 해당 카테고리의 TOP 100 조회
        List<UserCategoryPoint> top100User = userCategoryPointRepository.findTop100ByCategoryOrderByPointDesc(category);

        // 4. 응답 조립
        // 4-1. 필터 응답 조립
        RankingFilterResponseDto rankingFilter = new RankingFilterResponseDto(
                null,
                category
        );

        // 4-2. 내 랭킹 응답 조립
        MyRankingResponseDto myRanking = new MyRankingResponseDto(
                userId,
                user.getUsername(),
                user.getProfileImageUrl(),
                myRank,
                myPoint
        );

        // 4-3. TOP 100 응답 조립
        List<RankingCommonResponseDto> top100rankings = new ArrayList<>();

        Long previousRanking = 0L;

        for (int i = 0; i < top100User.size(); i++) {

            Long rankings;

            if (i > 0 && top100User.get(i).getPoint().equals(top100User.get(i - 1).getPoint())) {

                rankings = previousRanking;

            } else {
                rankings = Long.valueOf(i + 1);
            }

            previousRanking = rankings;

            RankingCommonResponseDto rankingCommonResponse =
                    new RankingCommonResponseDto(
                            top100User.get(i).getUser().getUsername(),
                            top100User.get(i).getUser().getProfileImageUrl(),
                            rankings,
                            top100User.get(i).getPoint()
                    );

            top100rankings.add(rankingCommonResponse);
        }

        // 5. 최종 응답 조립
        GlobalRankingResponseDto globalRanking = new GlobalRankingResponseDto(
                rankingFilter,
                myRanking,
                top100rankings
        );

        return globalRanking;
    }
}
