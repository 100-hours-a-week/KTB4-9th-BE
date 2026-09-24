package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import java.util.Map;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping("/{problemId}")
    public ResponseEntity<ApiResponse<Map<String, ProblemDetailResponse>>> getProblemDetail(@PathVariable String problemId) {
        // 1. 경로의 problemId를 숫자로 변환하고, 조회는 Service에게 맡김
        ProblemDetailResponse response = problemService.getProblemDetail(parseProblemId(problemId));

        // 2. 응답을 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("problem_detail_retrieval_success", Map.of("problem", response)));
    }

    // problemId 문자열을 숫자로 변환
    private Long parseProblemId(String problemId) {
        try {
            // 1. 문자열을 숫자로 변환
            return Long.parseLong(problemId);
        } catch (NumberFormatException e) {
            // 2. 숫자가 아니면 400 예외를 던짐 (GlobalExceptionHandler가 응답으로 변환)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_problem_id");
        }
    }

    @PostMapping("")
    public ResponseEntity<Void> createProblems(
            @Valid
            @RequestBody AiProblemsCreateRequestDto problemCreateRequest) {

        problemService.createProblems(problemCreateRequest);

        return ResponseEntity.noContent().build();
    }
}
