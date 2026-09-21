package com.cosmos.cosmos_backend.problem.domain;

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

/** 문제별 자연어 풀이 평가용 핵심 키워드 엔티티. */
@Entity
@Table(
        name = "keywords",
        uniqueConstraints = @UniqueConstraint(columnNames = {"problem_id", "keyword"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Keyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false, length = 255)
    private String keyword;

    public Keyword(Long problemId, String keyword) {
        this.problemId = problemId;
        this.keyword = keyword;
    }
}
