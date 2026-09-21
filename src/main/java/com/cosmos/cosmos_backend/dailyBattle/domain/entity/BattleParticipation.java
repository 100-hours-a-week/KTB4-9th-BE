package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "battle_participations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_battle_participation_battle_user_id",
                columnNames = {"user_id", "battle_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participationId;

    @OneToOne
    @JoinColumn(name = "battle_id", nullable = false)
    DailyBattle dailyBattle;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(nullable = true)
    private Boolean allCorrect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipationStatus participationStatus;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(nullable = true, updatable = false)
    private LocalDateTime submittedAt;


    public BattleParticipation(DailyBattle dailyBattle, User user) {
        this.dailyBattle = dailyBattle;
        this.user = user;
        this.participationStatus = ParticipationStatus.IN_PROGRESS;

    }

}
