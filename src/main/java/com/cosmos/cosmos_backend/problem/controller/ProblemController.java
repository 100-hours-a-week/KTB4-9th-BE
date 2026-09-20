package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping("/{problemId}")
    public ResponseEntity<ApiResponse<ProblemDetailResponse>> getProblemDetail(@PathVariable String problemId) {
        ProblemDetailResponse response = problemService.getProblemDetail(parseProblemId(problemId));
        return ResponseEntity.ok(ApiResponse.of("problem_detail_retrieval_success", response));
    }

    // problemId 문자열을 숫자로 변환
    private Long parseProblemId(String problemId) {
        try {
            return Long.parseLong(problemId);
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_problem_id");
        }
    }
}
