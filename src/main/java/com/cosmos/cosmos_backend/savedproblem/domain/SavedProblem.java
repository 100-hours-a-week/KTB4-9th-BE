package com.cosmos.cosmos_backend.savedproblem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 저장한 문제. 같은 사용자가 같은 문제를 중복 저장할 수 없음. */
@Entity
@Table(
        name = "saved_problems",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    public SavedProblem(Long userId, Long problemId) {
        this.userId = userId;
        this.problemId = problemId;
        this.savedAt = LocalDateTime.now();
    }
}
