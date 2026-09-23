package com.cosmos.cosmos_backend.problem.domain.entity;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @Column(nullable = false, length = 125)
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(nullable = false, length = 2048)
    private String content;

    @Column(name = "input_format", nullable = false, columnDefinition = "TEXT")
    private String inputFormat;

    @Column(name = "output_format", nullable = false, columnDefinition = "TEXT")
    private String outputFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<AiProblemsCreateRequestDto.InputConstraints> constraints;

    @Column(name = "category_select_reason", nullable = false, length = 100)
    private String categorySelectReason;

    public Problem(Difficulty difficulty, Category category, String title, String content, String inputFormat, String outputFormat, List<AiProblemsCreateRequestDto.InputConstraints> constraints, String categorySelectReason) {
        this.difficulty = difficulty;
        this.category = category;
        this.title = title;
        this.content = content;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.constraints = constraints;
        this.categorySelectReason = categorySelectReason;
    }
}
