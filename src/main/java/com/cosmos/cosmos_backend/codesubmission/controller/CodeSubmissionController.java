package com.cosmos.cosmos_backend.codesubmission.controller;

import com.cosmos.cosmos_backend.codesubmission.client.Judge0Properties;
import com.cosmos.cosmos_backend.codesubmission.dto.request.CodeSubmitRequest;
import com.cosmos.cosmos_backend.codesubmission.dto.response.CodeSubmitResponse;
import com.cosmos.cosmos_backend.codesubmission.service.CodeSubmissionService;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/problems/{problemId}/code-submissions")
@RequiredArgsConstructor
@Tag(name = "코드 제출", description = "소스 코드 제출 및 채점 API")
public class CodeSubmissionController {

    private static final int MAX_JUDGING_BATCHES = 3;
    private static final Duration ASYNC_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private final CodeSubmissionService codeSubmissionService;
    private final AsyncTaskExecutor codeSubmissionExecutor;
    private final Judge0Properties judge0Properties;

    @PostMapping
    @Operation(summary = "코드 제출 및 채점", description = "문제 풀이 코드를 제출하고 테스트 케이스 채점 결과를 반환합니다.")
    public WebAsyncTask<ResponseEntity<ApiResponse<CodeSubmitResponse>>> submit(
            @Parameter(description = "문제 ID", required = true, example = "1") @PathVariable String problemId,
            @Valid @RequestBody CodeSubmitRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        // 1. 요청 스레드에서 필요한 값을 먼저 꺼냄 (problemId 형식 오류는 여기서 바로 400)
        Long id = parseProblemId(problemId);
        Long userId = Long.valueOf(jwt.getSubject());
        String language = request.language();
        String sourceCode = request.sourceCode();

        // 2. 배치가 여러 개여도 Judge0 최악 시간보다 길게 비동기 타임아웃을 계산
        long timeoutMillis = judge0Properties.worstCaseJudgingTime(MAX_JUDGING_BATCHES)
                .plus(ASYNC_TIMEOUT_MARGIN)
                .toMillis();

        // 3. 채점은 전용 가상 스레드에서 실행하고 Tomcat 스레드는 바로 반환
        WebAsyncTask<ResponseEntity<ApiResponse<CodeSubmitResponse>>> task =
                new WebAsyncTask<>(timeoutMillis, codeSubmissionExecutor, () -> {
                    CodeSubmissionService.CodeSubmissionResult result =
                            codeSubmissionService.submit(userId, id, language, sourceCode);
                    return ResponseEntity.ok(ApiResponse.of("code_submission_completed",
                            CodeSubmitResponse.of(result.submission(), result.testResults())));
                });

        // 4. 타임아웃이면 Judge0 오류와 같은 503으로 응답 (GlobalExceptionHandler가 변환)
        task.onTimeout(() -> {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "judge_server_unavailable");
        });
        return task;
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
