package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import com.cosmos.cosmos_backend.common.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_battles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_battles_battle_date",
                        columnNames = "battle_date"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyBattle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long battleId;

    @Column(name = "battle_date", nullable = false)
    private LocalDate battleDate;

    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public DailyBattle(
            LocalDate battleDate,
            Category category,
            String title,
            String content
    ){
        this.battleDate = battleDate;
        this.category = category;
        this.title = title;
        this.content = content;
    }


}
