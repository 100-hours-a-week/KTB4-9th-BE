package com.cosmos.cosmos_backend.savedproblem.controller;

import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse;
import com.cosmos.cosmos_backend.savedproblem.service.SavedProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "저장 문제", description = "문제 저장·해제와 저장 목록 조회 API")
public class SavedProblemController {

    private final SavedProblemService savedProblemService;

    @PostMapping("/problems/{problemId}/saved")
    @Operation(summary = "문제 저장", description = "문제를 내 저장 목록에 추가합니다. 이미 저장돼 있으면 200, 새로 저장하면 201을 반환합니다.")
    public ResponseEntity<ApiResponse<Void>> save(
            @Parameter(description = "문제 ID", required = true, example = "1") @PathVariable String problemId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. JWT에서 사용자 id를 꺼내고, 경로의 problemId를 숫자로 변환
        Long userId = Long.valueOf(jwt.getSubject());
        Long id = parseProblemId(problemId);

        // 2. 저장은 Service에게 맡기고, 새로 저장했는지에 따라 201 또는 200으로 반환
        boolean created = savedProblemService.save(userId, id);
        HttpStatus status = created ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.of("problem_save_success"));
    }

    @DeleteMapping("/problems/{problemId}/saved")
    @Operation(summary = "문제 저장 해제", description = "내 저장 목록에서 문제를 제거합니다. 저장돼 있지 않아도 204를 반환합니다.")
    public ResponseEntity<Void> unsave(
            @Parameter(description = "문제 ID", required = true, example = "1") @PathVariable String problemId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. JWT에서 사용자 id를 꺼내고, 경로의 problemId를 숫자로 변환
        Long userId = Long.valueOf(jwt.getSubject());
        Long id = parseProblemId(problemId);

        // 2. 해제는 Service에게 맡기고 204로 반환
        savedProblemService.unsave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/saved-problems")
    @Operation(summary = "저장 문제 목록 조회", description = "내가 저장한 문제를 최근 저장순으로 조회합니다. level을 주면 해당 난이도만 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, List<SavedProblemResponse>>>> getSavedProblems(
            @Parameter(description = "난이도 필터 (LV1~LV5, 생략하면 전체)", example = "LV2") @RequestParam(required = false) Difficulty level,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. JWT에서 사용자 id를 꺼냄
        Long userId = Long.valueOf(jwt.getSubject());

        // 2. 조회는 Service에게 맡기고, message + data 형식으로 감싸서 200으로 반환
        List<SavedProblemResponse> problems = savedProblemService.getSavedProblems(userId, level);
        return ResponseEntity.ok(ApiResponse.of("saved_problems_retrieval_success", Map.of("problems", problems)));
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
