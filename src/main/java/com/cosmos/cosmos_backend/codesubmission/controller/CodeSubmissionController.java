package com.cosmos.cosmos_backend.codesubmission.controller;

import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.dto.request.CodeSubmitRequest;
import com.cosmos.cosmos_backend.codesubmission.dto.response.CodeSubmitResponse;
import com.cosmos.cosmos_backend.codesubmission.service.CodeSubmissionService;
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
@RequestMapping("/problems/{problemId}/code-submissions")
@RequiredArgsConstructor
public class CodeSubmissionController {

    private final CodeSubmissionService codeSubmissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CodeSubmitResponse>> submit(
            @PathVariable String problemId,
            @Valid @RequestBody CodeSubmitRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 경로의 problemId를 숫자로 변환
        Long id = parseProblemId(problemId);

        // 2. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 3. 채점은 Service에게 맡김 (Judge0 채점이 끝난 뒤에 반환됨)
        CodeSubmission submission = codeSubmissionService.submit(userId, id, request.language(), request.sourceCode());

        // 4. 채점 결과를 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("code_submission_completed", CodeSubmitResponse.of(submission)));
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
