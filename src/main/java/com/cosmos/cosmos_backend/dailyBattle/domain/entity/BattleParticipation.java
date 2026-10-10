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
                columnNames = {"user_id", "daily_battle_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participationId;

    @Column(name = "daily_battle_id")
    private Long dailyBattleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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


    public BattleParticipation(Long dailyBattleId, User user) {
        this.dailyBattleId = dailyBattleId;
        this.user = user;
        this.participationStatus = ParticipationStatus.IN_PROGRESS;

    }

    // 정답처리
    public void isCorrect(){
        this.allCorrect = true;
        this.participationStatus = ParticipationStatus.EVALUATING_COMPLETED;
    }

    // 오답처리
    public void isWrong(){
        this.allCorrect = false;
        this.participationStatus = ParticipationStatus.EVALUATING_COMPLETED;
    }

    // 중도 포기 처리
    public void dropOut(){
        this.participationStatus = ParticipationStatus.DROPPED_OUT;
    }

    // 타임아웃 처리
    public void timeOut(){
        this.participationStatus = ParticipationStatus.TIME_OVER;
    }

}
