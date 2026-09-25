package com.cosmos.cosmos_backend.problem.domain.entity;

import jakarta.persistence.CheckConstraint;
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

@Entity
@Table(
        name = "problem_examples",
        uniqueConstraints = @UniqueConstraint(columnNames = {"problem_id", "display_order"}),
        check = @CheckConstraint(name = "problem_examples_display_order_range", constraint = "display_order between 1 and 3")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false, length = 16)
    private String input;

    @Column(nullable = false, length = 16)
    private String output;

    @Column(length = 255)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public ProblemExample(Long problemId, String input, String output, String description, Integer displayOrder) {
        this.problemId = problemId;
        this.input = input;
        this.output = output;
        this.description = description;
        this.displayOrder = displayOrder;
    }
}
