package com.cosmos.cosmos_backend.problem.controller;

import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemSelectionResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemSelectionService;
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
public class ProblemSelectionController {

    private final ProblemSelectionService problemSelectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProblemSelectionResponse>> selectProblem(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. Spring Security가 검증한 JWT에서 로그인한 사용자 id(sub)를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 선택 처리는 Service에게 맡김
        ProblemSelectionResponse response = problemSelectionService.select(userId, level, category);

        // 3. message + data 형식으로 감싸서 200으로 반환
        return ResponseEntity.ok(ApiResponse.of("problem_selection_success", response));
    }
}
