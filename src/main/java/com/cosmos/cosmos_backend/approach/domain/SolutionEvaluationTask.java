package com.cosmos.cosmos_backend.approach.domain;

import com.cosmos.cosmos_backend.common.Category;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 자연어 풀이 AI 평가 작업. 제출 접수부터 평가 완료까지의 실행 상태를 관리. */
@Entity
@Table(
        name = "solution_evaluation_tasks",
        // 같은 사용자·문제에 active_slot=1인 작업은 하나만 존재 (최종 상태는 NULL이라 여러 개 가능)
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evaluation_tasks_active",
                columnNames = {"user_id", "problem_id", "active_slot"}),
        // 워커가 다음 작업을 찾을 때 쓰는 인덱스
        indexes = @Index(
                name = "idx_evaluation_tasks_poll",
                columnList = "status, next_retry_at, created_at"),
        check = {
                @CheckConstraint(name = "evaluation_tasks_attempt_count_non_negative", constraint = "worker_attempt_count >= 0"),
                @CheckConstraint(name = "evaluation_tasks_active_slot_valid", constraint = "active_slot is null or active_slot = 1")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolutionEvaluationTask {

    // 활성 작업(PENDING, PROCESSING, RETRY_WAIT)이 갖는 active_slot 값
    public static final int ACTIVE_SLOT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    // 평가 성공 후 반영된 제출 행 id (성공 전에는 NULL)
    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_category", nullable = false, length = 125)
    private Category selectedCategory;

    @Column(name = "natural_solution", nullable = false, columnDefinition = "TEXT")
    private String naturalSolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationTaskStatus status;

    // 워커가 AI 평가를 시작한 횟수 (사용자 제출 횟수와 무관)
    @Column(name = "worker_attempt_count", nullable = false)
    private Integer workerAttemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    // 활성 상태면 1, 최종 상태(SUCCEEDED, FAILED)면 NULL
    @Column(name = "active_slot")
    private Integer activeSlot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 사용자가 평가를 접수했을 때 생성. PENDING 상태로 시작. */
    public SolutionEvaluationTask(Long userId, Long problemId, Category selectedCategory, String naturalSolution, LocalDateTime now) {
        this.userId = userId;
        this.problemId = problemId;
        this.selectedCategory = selectedCategory;
        this.naturalSolution = naturalSolution;
        this.status = EvaluationTaskStatus.PENDING;
        this.workerAttemptCount = 0;
        this.activeSlot = ACTIVE_SLOT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 워커가 작업을 가져가 평가를 시작. PENDING 또는 RETRY_WAIT에서만 가능. */
    public void startProcessing(LocalDateTime now) {
        // 1. 대기 중인 작업만 시작할 수 있음
        if (status != EvaluationTaskStatus.PENDING && status != EvaluationTaskStatus.RETRY_WAIT) {
            throw illegalTransition(EvaluationTaskStatus.PROCESSING);
        }
        // 2. 처리 중으로 바꾸고 시작 시각·시도 횟수를 기록
        this.status = EvaluationTaskStatus.PROCESSING;
        this.workerAttemptCount += 1;
        this.startedAt = now;
        this.nextRetryAt = null;
        this.updatedAt = now;
    }

    /** 일시 오류로 재시도 대기에 들어감. PROCESSING에서만 가능. 활성 상태는 유지됨. */
    public void scheduleRetry(LocalDateTime nextRetryAt, String errorCode, String errorMessage, LocalDateTime now) {
        // 1. 처리 중인 작업만 재시도 대기로 보낼 수 있음
        if (status != EvaluationTaskStatus.PROCESSING) {
            throw illegalTransition(EvaluationTaskStatus.RETRY_WAIT);
        }
        // 2. 다음 시도 시각과 오류 내용을 기록
        this.status = EvaluationTaskStatus.RETRY_WAIT;
        this.nextRetryAt = nextRetryAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
    }

    /** AI 평가와 결과 저장이 성공. PROCESSING에서만 가능. 활성 상태가 해제됨. */
    public void markSucceeded(Long submissionId, LocalDateTime now) {
        // 1. 처리 중인 작업만 성공 처리할 수 있음
        if (status != EvaluationTaskStatus.PROCESSING) {
            throw illegalTransition(EvaluationTaskStatus.SUCCEEDED);
        }
        // 2. 성공 상태로 바꾸고 이전 재시도 오류 기록을 지움
        this.status = EvaluationTaskStatus.SUCCEEDED;
        this.submissionId = submissionId;
        this.errorCode = null;
        this.errorMessage = null;
        this.nextRetryAt = null;
        // 3. 활성 상태를 풀어 같은 문제를 다시 제출할 수 있게 함
        this.activeSlot = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    /** 최종 실패. PROCESSING에서만 가능. 활성 상태가 해제됨. */
    public void markFailed(String errorCode, String errorMessage, LocalDateTime now) {
        // 1. 처리 중인 작업만 실패 처리할 수 있음
        if (status != EvaluationTaskStatus.PROCESSING) {
            throw illegalTransition(EvaluationTaskStatus.FAILED);
        }
        // 2. 실패 상태로 바꾸고 오류 내용을 기록
        this.status = EvaluationTaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.nextRetryAt = null;
        // 3. 활성 상태를 풀어 다시 제출할 수 있게 함
        this.activeSlot = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    // 허용되지 않는 상태 변경 예외 생성
    private IllegalStateException illegalTransition(EvaluationTaskStatus target) {
        return new IllegalStateException("evaluation task " + id + ": " + status + " -> " + target + " is not allowed");
    }
}
