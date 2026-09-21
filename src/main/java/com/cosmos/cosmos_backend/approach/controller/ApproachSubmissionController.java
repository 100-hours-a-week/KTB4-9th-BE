package com.cosmos.cosmos_backend.approach.controller;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.request.ApproachSubmitRequest;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.service.ApproachSubmissionService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/problems/{problemId}/solution-submissions")
@RequiredArgsConstructor
public class ApproachSubmissionController {

    private final ApproachSubmissionService approachSubmissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApproachSubmitResponse>> submit(
            @PathVariable String problemId,
            @Valid @RequestBody ApproachSubmitRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 경로의 problemId를 숫자로 변환
        Long id = parseProblemId(problemId);

        // 2. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 3. 제출 처리는 Service에게 맡김
        ApproachSubmission submission = approachSubmissionService.submit(
                userId, id, request.selectedCategory(), request.naturalSolution()
        );

        // 4. 제출 결과를 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("solution_submission_success", ApproachSubmitResponse.of(submission)));
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
}
