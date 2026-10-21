package com.cosmos.cosmos_backend.approach.worker;

import com.cosmos.cosmos_backend.approach.service.SolutionEvaluationExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 오래 PROCESSING으로 멈춰 있는 평가 작업을 찾아 복구. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionEvaluationRecoverer {

    private final SolutionEvaluationExecutionService executionService;

    /** 멈춘 작업을 한 건씩 복구. 한 건이 실패해도 나머지는 계속 처리. */
    public void recover() {
        for (Long taskId : executionService.findStuckTaskIds()) {
            try {
                executionService.recoverStuck(taskId);
            } catch (Exception e) {
                log.error("멈춘 평가 작업 복구 실패: taskId={}", taskId, e);
            }
        }
    }
}
