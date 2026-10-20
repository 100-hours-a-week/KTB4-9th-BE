package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationFailedException;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.EvaluationFailureCode;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.ClaimedTask;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService.EvaluationInput;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** DB에 쌓인 평가 작업을 하나씩 가져와 AI 평가를 수행하는 워커. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionEvaluationWorker {

    private final SolutionEvaluationExecutionService executionService;
    private final AiEvaluationClient aiEvaluationClient;

    /** 처리할 작업이 없을 때까지 연속으로 처리. */
    public void runUntilEmpty() {
        while (!Thread.currentThread().isInterrupted() && processNext()) {
            // 작업을 하나 끝낼 때마다 쉬지 않고 바로 다음 작업을 확인
        }
    }

    // 작업 하나를 처리하고, 처리한 작업이 있었으면 true
    private boolean processNext() {
        // 1. 작업 선점 (없으면 종료)
        Optional<ClaimedTask> claimed = executionService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedTask task = claimed.get();

        try {
            // 2. AI 요청 준비
            EvaluationInput input = executionService.prepare(task.taskId());

            // 3. AI 평가 호출 (트랜잭션 밖이라 DB 커넥션을 잡지 않음)
            AiEvaluationResult result = aiEvaluationClient.evaluate(input.request());

            // 4. 결과 저장
            executionService.complete(task.taskId(), task.attempt(), input.keywords(), result);
        } catch (Exception e) {
            // 5. 어느 단계든 실패하면 실패를 기록하고 다음 작업으로 넘어감
            log.error("자연어 풀이 평가 실패: taskId={}, attempt={}", task.taskId(), task.attempt(), e);
            recordFailure(task, e);
        }
        return true;
    }

    // 예외를 실패 코드와 재시도 여부로 바꿔 기록 (이것마저 실패해도 워커는 멈추지 않음)
    private void recordFailure(ClaimedTask task, Exception cause) {
        EvaluationFailureCode code;
        boolean retryable;
        String message;
        if (cause instanceof AiEvaluationFailedException ai) {
            // AI 오류는 클라이언트가 분류한 그대로 사용
            code = ai.getFailureCode();
            retryable = ai.isRetryable();
            message = ai.getDetail() != null ? ai.getDetail() : ai.getMessage();
        } else if (cause instanceof BusinessException business) {
            // 우리 쪽 규칙 위반(한도 초과 등)은 다시 해도 같은 결과
            code = EvaluationFailureCode.INTERNAL_ERROR;
            retryable = false;
            message = business.getMessage();
        } else {
            // 그 외 예기치 못한 오류(DB 일시 장애 등)는 재시도
            code = EvaluationFailureCode.INTERNAL_ERROR;
            retryable = true;
            message = cause.getClass().getSimpleName();
        }

        try {
            executionService.handleFailure(task.taskId(), task.attempt(), code, message, retryable);
        } catch (Exception e) {
            log.error("평가 실패 상태 저장 실패: taskId={}", task.taskId(), e);
        }
    }
}
