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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 문제 풀이(자연어 접근 방식) 제출. */
@Entity
@Table(
        name = "problem_solution_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}),
        check = @CheckConstraint(name = "problem_solution_submissions_submitted_count_positive", constraint = "submitted_count >= 1")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApproachSubmission {

    // 한 문제에 제출할 수 있는 최대 횟수 (첫 제출, 재제출, 복습 제출 모두 포함)
    public static final int MAX_SUBMISSION_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_category", nullable = false, length = 125)
    private Category selectedCategory;

    @Column(name = "natural_solution", nullable = false, columnDefinition = "TEXT")
    private String naturalSolution;

    @Column(name = "category_result", nullable = false)
    private Boolean categoryResult;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 64)
    private EvaluationStatus evaluationStatus;

    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    /** AI 평가가 성공했을 때만 호출됨. 생성과 동시에 COMPLETED로 채움. */
    public ApproachSubmission(Long userId, Long problemId, Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback) {
        this.userId = userId;
        this.problemId = problemId;
        this.selectedCategory = selectedCategory;
        this.naturalSolution = naturalSolution;
        this.categoryResult = categoryResult;
        this.submittedCount = 1;
        this.submittedAt = LocalDateTime.now();
        this.totalScore = score;
        this.aiFeedback = feedback;
        this.evaluationStatus = EvaluationStatus.COMPLETED;
        this.evaluatedAt = LocalDateTime.now();
    }

    /** 재제출 처리. AI 평가가 성공했을 때만 호출됨. 이전 결과를 새 결과로 덮어씀. */
    public void resubmit(Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback) {
        this.selectedCategory = selectedCategory;
        this.naturalSolution = naturalSolution;
        this.categoryResult = categoryResult;
        this.submittedCount += 1;
        this.submittedAt = LocalDateTime.now();
        this.totalScore = score;
        this.aiFeedback = feedback;
        this.evaluationStatus = EvaluationStatus.COMPLETED;
        this.evaluatedAt = LocalDateTime.now();
    }
}
