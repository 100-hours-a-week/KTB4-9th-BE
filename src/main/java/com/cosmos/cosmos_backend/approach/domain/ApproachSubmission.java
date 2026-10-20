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
import java.time.ZoneOffset;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keyword_results", columnDefinition = "json")
    private List<KeywordResult> keywordResults;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 64)
    private EvaluationStatus evaluationStatus;

    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount;

    @Column(name = "is_solved", nullable = false)
    private Boolean isSolved = false;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    /** AI 평가가 성공했을 때만 호출됨. 생성과 동시에 COMPLETED로 채움. 제출 시각은 지금으로 채움. */
    public ApproachSubmission(Long userId, Long problemId, Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback) {
        this(userId, problemId, selectedCategory, naturalSolution, categoryResult, score, feedback, LocalDateTime.now(ZoneOffset.UTC));
    }

    /** 평가 작업의 접수 시각을 제출 시각으로 지정해 생성 (비동기 평가 워커가 사용). */
    public ApproachSubmission(Long userId, Long problemId, Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback, LocalDateTime submittedAt) {
        this.userId = userId;
        this.problemId = problemId;
        this.selectedCategory = selectedCategory;
        this.naturalSolution = naturalSolution;
        this.categoryResult = categoryResult;
        this.submittedCount = 1;
        this.submittedAt = submittedAt;
        this.totalScore = score;
        this.aiFeedback = feedback;
        this.evaluationStatus = EvaluationStatus.COMPLETED;
        this.evaluatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 재제출 처리. AI 평가가 성공했을 때만 호출됨. 이전 결과를 새 결과로 덮어씀. */
    public void resubmit(Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback) {
        resubmit(selectedCategory, naturalSolution, categoryResult, score, feedback, LocalDateTime.now(ZoneOffset.UTC));
    }

    /** 평가 작업의 접수 시각을 제출 시각으로 지정해 재제출 처리 (비동기 평가 워커가 사용). */
    public void resubmit(Category selectedCategory, String naturalSolution, boolean categoryResult, int score, String feedback, LocalDateTime submittedAt) {
        this.selectedCategory = selectedCategory;
        this.naturalSolution = naturalSolution;
        this.categoryResult = categoryResult;
        this.submittedCount += 1;
        this.submittedAt = submittedAt;
        this.totalScore = score;
        this.aiFeedback = feedback;
        this.evaluationStatus = EvaluationStatus.COMPLETED;
        this.evaluatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 이번 제출의 AI 키워드 판정을 저장. 이전 값은 새 값으로 교체됨. */
    public void updateKeywordResults(List<KeywordResult> keywordResults) {
        this.keywordResults = keywordResults;
    }

    public void markAsSolved() {
        this.isSolved = true;
    }
}
