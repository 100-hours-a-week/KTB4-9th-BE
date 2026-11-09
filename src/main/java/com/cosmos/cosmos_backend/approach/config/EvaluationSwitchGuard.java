package com.cosmos.cosmos_backend.approach.config;

import org.springframework.stereotype.Component;

/** 기존 워커와 MQ 방식을 동시에 켜면 서버를 시작하지 않음. */
@Component
public class EvaluationSwitchGuard {

    public EvaluationSwitchGuard(EvaluationWorkerProperties worker, EvaluationMqProperties mq) {
        if (worker.enabled() && mq.enabled()) {
            throw new IllegalStateException(
                    "EVALUATION_WORKER_ENABLED와 EVALUATION_MQ_ENABLED를 동시에 켤 수 없음");
        }
    }
}
