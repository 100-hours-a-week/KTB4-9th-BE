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
        Long id = parseProblemId(problemId);
        Long userId = Long.valueOf(jwt.getSubject());
        ApproachSubmission submission = approachSubmissionService.submit(
                userId, id, request.selectedCategory(), request.approach()
        );
        return ResponseEntity.ok(ApiResponse.of("solution_submission_success", ApproachSubmitResponse.of(submission)));
    }

    private Long parseProblemId(String problemId) {
        try {
            return Long.parseLong(problemId);
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_problem_id");
        }
    }
}
