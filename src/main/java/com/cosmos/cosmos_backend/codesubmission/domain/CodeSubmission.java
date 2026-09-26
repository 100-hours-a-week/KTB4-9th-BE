package com.cosmos.cosmos_backend.codesubmission.domain;

import com.cosmos.cosmos_backend.common.Language;
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

/** 사용자별 문제 코드 제출과 채점 결과. */
@Entity
@Table(
        name = "code_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}),
        check = {
                @CheckConstraint(name = "code_submissions_submitted_count_positive", constraint = "submitted_count >= 1"),
                @CheckConstraint(
                        name = "code_submissions_test_count_valid",
                        constraint = "passed_test_count >= 0 and total_test_count >= 0 and passed_test_count <= total_test_count"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeSubmission {

    /** 한 문제에 제출할 수 있는 최대 횟수. */
    public static final int MAX_SUBMISSION_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "judging_status", nullable = false, length = 20)
    private JudgingStatus judgingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "judging_result", length = 20)
    private JudgingResult judgingResult;

    @Column(name = "passed_test_count")
    private Integer passedTestCount;

    @Column(name = "total_test_count")
    private Integer totalTestCount;

    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    /** 채점이 성공했을 때만 호출됨. 생성과 동시에 COMPLETED로 채움. */
    public CodeSubmission(Long userId, Long problemId, Language language, String sourceCode,
                          LocalDateTime submittedAt, JudgingResult result, int passedTestCount, int totalTestCount) {
        this.userId = userId;
        this.problemId = problemId;
        this.submittedCount = 1;
        applyResult(language, sourceCode, submittedAt, result, passedTestCount, totalTestCount);
    }

    /** 재제출 처리. 채점이 성공했을 때만 호출됨. 이전 결과를 새 결과로 덮어씀. */
    public void resubmit(Language language, String sourceCode,
                         LocalDateTime submittedAt, JudgingResult result, int passedTestCount, int totalTestCount) {
        this.submittedCount += 1;
        applyResult(language, sourceCode, submittedAt, result, passedTestCount, totalTestCount);
    }

    // 제출 내용과 채점 결과를 채움 (첫 제출과 재제출이 같이 사용)
    private void applyResult(Language language, String sourceCode,
                             LocalDateTime submittedAt, JudgingResult result, int passedTestCount, int totalTestCount) {
        this.language = language;
        this.sourceCode = sourceCode;
        this.submittedAt = submittedAt;
        this.judgingStatus = JudgingStatus.COMPLETED;
        this.judgingResult = result;
        this.passedTestCount = passedTestCount;
        this.totalTestCount = totalTestCount;
        this.judgedAt = LocalDateTime.now();
    }
}
