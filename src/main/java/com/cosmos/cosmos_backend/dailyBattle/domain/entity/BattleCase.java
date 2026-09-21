package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "battle_cases",
        uniqueConstraints = {@UniqueConstraint(
                name = "uk_battle_cases_battle_order",
                columnNames = {"battle_id", "display_order"}
        )})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long caseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id", nullable = false)
    private DailyBattle dailyBattle;

    @Column(nullable = false)
    private String input;

    @Column(name = "expected_output", nullable = false)
    private String expectedOutput;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public BattleCase(
            DailyBattle dailyBattle,
            String input,
            String expectedOutput,
            Integer displayOrder
    ) {
        this.dailyBattle = dailyBattle;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.displayOrder = displayOrder;
    }
}
