package com.cosmos.cosmos_backend.problem.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(name = "daily_problems",
uniqueConstraints = @UniqueConstraint(columnNames = {"recommend_date", "display_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "problem_id",
            nullable = false,
            unique = true
    )
    private Problem problem;

    @Column(nullable = false, name = "recommend_date")
    LocalDate recommendDate;

    @Column(nullable = false, name = "display_order")
    Integer displayOrder;

    public DailyProblem(Problem problem, LocalDate recommendDate, Integer displayOrder) {
        this.problem = problem;
        this.recommendDate = recommendDate;
        this.displayOrder = displayOrder;
    }

}
