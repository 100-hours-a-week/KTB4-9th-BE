package com.cosmos.cosmos_backend.approach.controller;

import com.cosmos.cosmos_backend.approach.dto.response.SolutionEvaluationResponse;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationTaskService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/solution-evaluations")
@RequiredArgsConstructor
@Tag(name = "풀이 평가", description = "자연어 풀이 평가 작업 조회 API")
public class SolutionEvaluationController {

    private final SolutionEvaluationTaskService solutionEvaluationTaskService;

    @GetMapping("/{evaluationTaskId}")
    @Operation(summary = "평가 작업 조회", description = "접수한 풀이 평가의 진행 상태와 결과를 조회합니다. 본인이 접수한 작업만 조회할 수 있습니다.")
    public ResponseEntity<ApiResponse<SolutionEvaluationResponse>> get(
            @Parameter(description = "평가 작업 ID", required = true, example = "1") @PathVariable String evaluationTaskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 경로의 평가 작업 id를 숫자로 변환
        Long taskId = parseTaskId(evaluationTaskId);

        // 2. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 3. 조회는 Service에게 맡기고 message + data 형식으로 감싸서 200으로 반환 (진행 상태와 상관없이 200)
        return ResponseEntity.ok(ApiResponse.of("solution_evaluation_retrieval_success",
                solutionEvaluationTaskService.get(userId, taskId)));
    }

    // 평가 작업 id 문자열을 숫자로 변환
    private Long parseTaskId(String evaluationTaskId) {
        try {
            // 1. 문자열을 숫자로 변환
            return Long.parseLong(evaluationTaskId);
        } catch (NumberFormatException e) {
            // 2. 숫자가 아니면 400 예외를 던짐 (GlobalExceptionHandler가 응답으로 변환)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_evaluation_task_id");
        }
    }
}
