package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemSelectionResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemSelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
@Tag(name = "문제 선택", description = "조건에 맞는 미풀이 문제 선택 API")
public class ProblemSelectionController {

    private final ProblemSelectionService problemSelectionService;

    @GetMapping("/daily-usage")
    @Operation(summary = "오늘 문제 생성 사용 현황", description = "오늘 문제 생성 횟수를 조회합니다. 횟수는 올라가지 않습니다.")
    public ResponseEntity<ApiResponse<ProblemSelectionResponse.DailyUsageStatus>> getDailyUsage(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 조회는 Service에게 맡김
        ProblemSelectionResponse.DailyUsageStatus response = problemSelectionService.getDailyUsage(userId);

        // 3. message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("daily_usage_retrieval_success", response));
    }

    @GetMapping
    @Operation(summary = "문제 선택", description = "필수 난이도와 선택 카테고리 조건에 맞는 미풀이 문제를 선택합니다. 카테고리를 생략하면 전체 카테고리 중 선택합니다.")
    public ResponseEntity<ApiResponse<ProblemSelectionResponse>> selectProblem(
            @Parameter(description = "난이도. LV1~LV5 또는 1~5 형식", required = true, example = "LV3") @RequestParam(required = false) String level,
            @Parameter(description = "알고리즘 카테고리. 생략하거나 RANDOM이면 전체 카테고리 중 선택합니다.", example = "DP") @RequestParam(required = false) String category,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 선택 처리는 Service에게 맡김
        ProblemSelectionResponse response = problemSelectionService.select(userId, level, category);

        // 3. message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("problem_selection_success", response));
    }
}
