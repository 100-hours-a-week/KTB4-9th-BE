package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
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
        Optional<Long> claimed = executionService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        Long taskId = claimed.get();

        try {
            // 2. AI 요청 준비
            EvaluationInput input = executionService.prepare(taskId);

            // 3. AI 평가 호출 (트랜잭션 밖이라 DB 커넥션을 잡지 않음)
            AiEvaluationResult result = aiEvaluationClient.evaluate(input.request());

            // 4. 결과 저장
            executionService.complete(taskId, input.keywords(), result);
        } catch (Exception e) {
            // 5. 어느 단계든 실패하면 작업을 실패 처리하고 다음 작업으로 넘어감
            log.error("자연어 풀이 평가 실패: taskId={}", taskId, e);
            markFailed(taskId, e);
        }
        return true;
    }

    // 실패 상태 저장 (이것마저 실패해도 워커는 멈추지 않음)
    private void markFailed(Long taskId, Exception cause) {
        // 우리가 던진 예외는 코드 이름(짧은 문구)을, 그 외는 예외 클래스 이름을 기록
        boolean business = cause instanceof BusinessException;
        String errorCode = business ? cause.getMessage() : "internal_error";
        String errorMessage = business ? cause.getMessage() : cause.getClass().getSimpleName();
        try {
            executionService.fail(taskId, errorCode, errorMessage);
        } catch (Exception e) {
            log.error("평가 실패 상태 저장 실패: taskId={}", taskId, e);
        }
    }
}
