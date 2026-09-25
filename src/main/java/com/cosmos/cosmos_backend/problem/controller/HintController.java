package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.dto.request.HintRequest;
import com.cosmos.cosmos_backend.problem.dto.response.HintResponse;
import com.cosmos.cosmos_backend.problem.service.HintService;
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
@RequestMapping("/problems/{problemId}/hints")
@RequiredArgsConstructor
public class HintController {

    private final HintService hintService;

    @PostMapping("/comment")
    public ResponseEntity<ApiResponse<HintResponse>> getCommentHint(
            @PathVariable String problemId,
            @Valid @RequestBody HintRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 주석 힌트 조회는 공통 처리에 맡김
        HintResponse response = retrieve(problemId, request, jwt, HintType.COMMENT);

        // 2. 응답을 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("comment_hint_retrieval_success", response));
    }

    @PostMapping("/answer")
    public ResponseEntity<ApiResponse<HintResponse>> getAnswerHint(
            @PathVariable String problemId,
            @Valid @RequestBody HintRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 정답 힌트 조회는 공통 처리에 맡김
        HintResponse response = retrieve(problemId, request, jwt, HintType.SOLUTION);

        // 2. 응답을 message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("answer_hint_retrieval_success", response));
    }

    // 두 API가 같이 쓰는 조회 처리
    private HintResponse retrieve(String problemId, HintRequest request, Jwt jwt, HintType hintType) {
        // 1. 경로의 problemId를 숫자로 변환
        Long id = parseProblemId(problemId);

        // 2. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 3. 조회는 Service에게 맡김
        return hintService.getHint(userId, id, request.language(), hintType);
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
