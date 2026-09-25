package com.cosmos.cosmos_backend.problem.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 문제 힌트 사용 단계 (1: 주석 힌트, 2: 정답 힌트). 언어와 관계없이 문제 단위로 관리. */
@Entity
@Table(
        name = "used_hints",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsedHint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "hint_stage", nullable = false)
    private Integer hintStage;

    public UsedHint(Long userId, Long problemId, int hintStage) {
        this.userId = userId;
        this.problemId = problemId;
        this.hintStage = hintStage;
    }

    /** 단계를 올리기만 함. 이미 그 단계 이상이면 그대로 둠. */
    public void raiseStageTo(int stage) {
        if (stage > this.hintStage) {
            this.hintStage = stage;
        }
    }
}
