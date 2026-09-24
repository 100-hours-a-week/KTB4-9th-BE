package com.cosmos.cosmos_backend.ranking.service;


import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.auth.repository.UserRepository;
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

    UserPointRepository userPointRepository;

    UserDifficultyPointRepository userDifficultyPointRepository;

    UserCategoryPointRepository userCategoryPointRepository;

    UserRepository userRepository;

    // 전체 랭킹 조회
    public GlobalRankingResponseDto getRanking(Long userId) {

        Optional<UserPoint> userPoint = userPointRepository.findByUser_UserId(userId);

        // 0. 유저 정보 조회
        User user = userRepository.findById(userId).get();

        // 1. 유저 개인 포인트 계산
        Long myPoint = userPoint.get().getTotalPoint();

        // 2. 유저 랭킹 계산
        Long myRank = userPointRepository.countByPointGreaterThan(myPoint);

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

        for(int i = 0 ; i < top100User.size() ; i++){
            RankingCommonResponseDto rankingCommonResponse = new RankingCommonResponseDto(
                    top100User.get(i).getUser().getUsername(),
                    top100User.get(i).getUser().getProfileImageUrl(),
                    Long.valueOf(i+1),
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

    public GlobalRankingResponseDto getDifficultyRanking(Long userId) {

        return null;
    }

    public GlobalRankingResponseDto getCategoryRanking(Long userId) {
        return null;
    }
}
