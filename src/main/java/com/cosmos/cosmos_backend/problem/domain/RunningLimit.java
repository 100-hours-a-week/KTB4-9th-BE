package com.cosmos.cosmos_backend.problem.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문제별 언어별 채점 실행 제한(시간/메모리) 엔티티. */
@Entity
@Table(
        name = "running_limits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"problem_id", "language"}),
        check = {
                @CheckConstraint(name = "running_limits_time_limit_ms_positive", constraint = "time_limit_ms > 0"),
                @CheckConstraint(name = "running_limits_memory_limit_mb_positive", constraint = "memory_limit_mb > 0")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private Integer memoryLimitMb;

    public RunningLimit(Long problemId, Language language, Integer timeLimitMs, Integer memoryLimitMb) {
        this.problemId = problemId;
        this.language = language;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
    }
}
