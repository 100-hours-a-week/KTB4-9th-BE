package com.cosmos.cosmos_backend.problem.domain.entity;

import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "hints",
        uniqueConstraints = @UniqueConstraint(columnNames = {"problem_id", "language", "hint_type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hint {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long hintId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Language language;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HintType hintType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public Hint(Long problemId, Language language, HintType hintType, String content) {
        this.problemId = problemId;
        this.language = language;
        this.hintType = hintType;
        this.content = content;
    }
}
