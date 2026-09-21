package com.cosmos.cosmos_backend.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(nullable = false, length = 125)
    private String category;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(nullable = false, length = 2048)
    private String content;

    @Column(nullable = false, columnDefinition = "json")
    private String constraints;

    @Column(name = "category_select_reason", nullable = false, length = 30)
    private String categorySelectReason;

    public Problem(String difficulty, String category, String title, String content, String constraints, String categorySelectReason) {
        this.difficulty = difficulty;
        this.category = category;
        this.title = title;
        this.content = content;
        this.constraints = constraints;
        this.categorySelectReason = categorySelectReason;
    }
}
