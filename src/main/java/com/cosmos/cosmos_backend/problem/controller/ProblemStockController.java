package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemStockResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/problems/new")
@RequiredArgsConstructor
@Tag(name = "문제 재고", description = "난이도·카테고리별 미풀이 문제 수 조회 API")
public class ProblemStockController {

    private final ProblemStockService problemStockService;

    @GetMapping
    @Operation(summary = "미풀이 문제 수 조회", description = "아직 아무도 해결하지 않은 문제 수를 난이도와 카테고리별로 조회합니다.")
    public ResponseEntity<ApiResponse<ProblemStockResponse>> getUnsolvedProblemCounts() {
        // 1. 난이도·카테고리별 "아무도 풀지 않은 문제 수"는 Service에게 맡김
        ProblemStockResponse response = problemStockService.getUnsolvedProblemCounts();

        // 2. message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("아무도 풀지 않은 문제 조회에 성공했습니다.", response));
    }
}
