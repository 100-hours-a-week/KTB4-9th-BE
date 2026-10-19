package com.cosmos.cosmos_backend.problem.domain.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 일일 문제 생성 횟수 (KST 기준 날짜별 1행). */
@Entity
@Table(
        name = "daily_generated_counts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date"}),
        check = @CheckConstraint(name = "daily_generated_counts_count_range", constraint = "generation_count between 1 and 3")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyGeneratedCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "generation_count", nullable = false)
    private Integer generationCount;

    // 오늘 마지막으로 생성한 문제 (이 칼럼을 추가하기 전에 만들어진 행은 NULL)
    @Column(name = "last_problem_id")
    private Long lastProblemId;

    // 그날 처음 사용할 때 횟수 1로 생성하고 받은 문제를 기록
    public DailyGeneratedCount(Long userId, LocalDate usageDate, Long problemId) {
        this.userId = userId;
        this.usageDate = usageDate;
        this.generationCount = 1;
        this.lastProblemId = problemId;
    }

    // 사용 횟수를 1 늘리고 받은 문제로 기록을 바꿈
    public void increase(Long problemId) {
        this.generationCount += 1;
        this.lastProblemId = problemId;
    }
}
