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
@Table(name = "test_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false, length = 16)
    private String input;

    @Column(name = "expected_output", nullable = false, length = 16)
    private String expectedOutput;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public TestCase(Long problemId, String input, String expectedOutput, Integer displayOrder) {
        this.problemId = problemId;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.displayOrder = displayOrder;
    }
}
