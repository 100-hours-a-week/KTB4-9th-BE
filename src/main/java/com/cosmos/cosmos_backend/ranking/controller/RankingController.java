package com.cosmos.cosmos_backend.ranking.controller;


import com.cosmos.cosmos_backend.ranking.dto.GlobalRankingResponseDto;
import com.cosmos.cosmos_backend.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    // 전체 랭킹 조회
    @GetMapping("/global")
    public GlobalRankingResponseDto getRanking(
            @AuthenticationPrincipal Jwt jwt
    ){
        Long userId = Long.parseLong(jwt.getSubject());

        GlobalRankingResponseDto rankingResponse = rankingService.getRanking(userId);

        return rankingResponse;
    }
}
