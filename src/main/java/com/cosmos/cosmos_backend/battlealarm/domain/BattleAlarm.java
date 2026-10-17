package com.cosmos.cosmos_backend.battlealarm.domain;

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
import org.hibernate.annotations.ColumnDefault;

/** 사용자별 배틀 알림 상태. 사용자가 해당 배틀의 알림 모달을 닫았는지 저장함. */
@Entity
@Table(
        name = "battle_alarms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_battle_alarms_user_battle",
                columnNames = {"user_id", "battle_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "battle_id", nullable = false)
    private Long battleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean dismissed;

    public BattleAlarm(Long userId, Long battleId) {
        this.userId = userId;
        this.battleId = battleId;
        this.dismissed = false;
    }

    /** 알림을 닫음 상태로 바꿈. 이미 닫았으면 그대로 둠. */
    public void dismiss() {
        // 1. 닫음 상태로 변경
        this.dismissed = true;
    }
}
