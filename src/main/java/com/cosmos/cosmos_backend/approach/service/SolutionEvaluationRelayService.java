package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.approach.config.EvaluationMqProperties;
import com.cosmos.cosmos_backend.approach.domain.EvaluationFailureCode;
import com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.notification.domain.NotificationType;
import com.cosmos.cosmos_backend.notification.service.NotificationService;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 릴레이가 쓰는 짧은 트랜잭션 모음. 발행과 Confirm 대기는 트랜잭션 밖에서 하므로 여기에 넣지 않음. */
@Service
@RequiredArgsConstructor
public class SolutionEvaluationRelayService {

    private final SolutionEvaluationTaskRepository taskRepository;
    private final ProblemRepository problemRepository;
    private final NotificationService notificationService;
    private final EvaluationMqProperties properties;
    private final Clock clock;

    /** 발행할 작업을 선점해 예약 시각을 적고 id 목록을 반환. */
    @Transactional
    public List<Long> claimForPublish(int limit) {
        // 1. 발행 대상을 잠그고 조회
        LocalDateTime now = now();
        List<SolutionEvaluationTask> targets = taskRepository.findPublishTargetsForUpdate(now, limit);

        // 2. 예약 시각을 적음 (커밋되면 잠금이 풀리고, 예약이 끝나기 전에는 다른 릴레이가 가져가지 않음)
        LocalDateTime reservedUntil = now.plus(properties.publishLease());
        targets.forEach(task -> task.reservePublish(reservedUntil, now));
        return targets.stream().map(SolutionEvaluationTask::getId).toList();
    }

    /** 발행 확인을 받은 작업을 PROCESSING으로 바꿈. 이미 끝났거나 발행 대기 상태가 아니면 건드리지 않고 false. */
    @Transactional
    public boolean markPublished(Long taskId) {
        // 1. 작업을 잠그고 발행 대기 중인지 확인 (결과가 먼저 도착해 끝난 작업은 되돌리지 않음)
        SolutionEvaluationTask task = lockTask(taskId);
        if (task.getStatus() != EvaluationTaskStatus.PENDING
                && task.getStatus() != EvaluationTaskStatus.RETRY_WAIT) {
            return false;
        }

        // 2. 처리 중으로 바꿈
        task.markPublished(now());
        return true;
    }

    /** 접수 후 제한 시간이 지났는데 아직 PENDING인 작업의 id 목록. */
    @Transactional(readOnly = true)
    public List<Long> findExpiredPendingIds() {
        return taskRepository.findExpiredPendingIds(now().minus(properties.pendingFailAfter()));
    }

    /** 제한 시간이 지난 PENDING 작업 1건을 실패 처리. 작업마다 별도 트랜잭션. */
    @Transactional
    public void failExpiredPending(Long taskId) {
        // 1. 작업을 잠그고, 그 사이 발행되었거나 아직 제한 시간 안이면 건드리지 않음
        SolutionEvaluationTask task = lockTask(taskId);
        LocalDateTime now = now();
        if (task.getStatus() != EvaluationTaskStatus.PENDING
                || !task.getCreatedAt().isBefore(now.minus(properties.pendingFailAfter()))) {
            return;
        }

        // 2. 실패 처리
        task.markFailed(EvaluationFailureCode.AI_SERVER_UNAVAILABLE.name(), "publish_failed", now);

        // 3. 실패 알림을 만듦 (같은 트랜잭션)
        Problem problem = problemRepository.findById(task.getProblemId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));
        notificationService.createEvaluationNotification(
                task.getUserId(), task.getProblemId(), task.getId(),
                NotificationType.NL_EVALUATION_FAILED, problem.getTitle());
    }

    // 작업을 잠그고 조회 (없으면 404 예외를 던짐)
    private SolutionEvaluationTask lockTask(Long taskId) {
        return taskRepository.findForUpdateById(taskId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "solution_evaluation_not_found"));
    }

    // 현재 시각 (UTC)
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
