package com.cosmos.cosmos_backend.ranking.controller;


import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.ranking.dto.GlobalRankingResponseDto;
import com.cosmos.cosmos_backend.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
@Tag(name = "랭킹", description = "전체·난이도·카테고리별 사용자 랭킹 API")
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    @Operation(summary = "랭킹 조회", description = "전체 랭킹 또는 난이도·카테고리별 랭킹과 내 순위를 조회합니다. 두 필터는 동시에 사용할 수 없습니다.")
    public GlobalRankingResponseDto getRanking(
            @Parameter(description = "난이도 필터", example = "LV3") @RequestParam(required = false) Difficulty difficulty,
            @Parameter(description = "알고리즘 카테고리 필터", example = "DP") @RequestParam(required = false) Category category,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {

        Long userId = Long.parseLong(jwt.getSubject());

        // 1. 난이도와 카테고리가 동시에 들어온 경우
        if (difficulty != null && category != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "difficulty와 category는 동시에 사용할 수 없습니다."
            );
        }

        // 2. 난이도 랭킹
        if (difficulty != null) {
            return rankingService.getDifficultyRanking(userId, difficulty);
        }

        // 3. 카테고리 랭킹
        if (category != null) {
            return rankingService.getCategoryRanking(userId, category);
        }

        // 4. 전체 랭킹
        return rankingService.getRanking(userId);
    }
}
