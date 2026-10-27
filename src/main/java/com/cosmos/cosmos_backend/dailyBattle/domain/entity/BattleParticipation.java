package com.cosmos.cosmos_backend.dailyBattle.domain.entity;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.dailyBattle.domain.ParticipationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.parameters.P;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

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
    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "daily_battle_id", nullable = false)
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
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Max(3)
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    // 임대시간
    @Column(name = "lease_time", columnDefinition = "DATETIME(3)")
    private LocalDateTime leaseTime;

    // 작업 선점 토큰
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "lease_token", columnDefinition = "BINARY(16)")
    private UUID leaseToken;



    public BattleParticipation(Long dailyBattleId, User user) {
        this.dailyBattleId = dailyBattleId;
        this.user = user;
        this.participationStatus = ParticipationStatus.IN_PROGRESS;

    }

    //제출 처리
    public void markSubmit(Instant submittedAt) {
        this.submittedAt = submittedAt;
        this.participationStatus = ParticipationStatus.SUBMITTED;
    }

    // 채점 중으로 처리 + 임대 시간 등록
    public void markGrading(LocalDateTime leaseTime, UUID leaseToken) {
        this.participationStatus = ParticipationStatus.GRADING;
        this.leaseTime = leaseTime;
        this.leaseToken = leaseToken;
    }

    // 채점 완료 처리
    public void markEvaluatingComplete(){
        this.participationStatus = ParticipationStatus.GRADING_COMPLETED;
    }

    // 채점 실패 처리
    public void markGradingFailed(){
        this.participationStatus = ParticipationStatus.GRADING_FAILED;
    }

    // 중도 포기 처리
    public void markDropOut(){
        this.participationStatus = ParticipationStatus.DROPPED_OUT;
    }

    // 타임아웃 처리
    public void markTimeOut(){
        this.participationStatus = ParticipationStatus.TIME_OVER;
    }

    // 정답처리
    public void isCorrect(){
        this.allCorrect = true;
        this.participationStatus = ParticipationStatus.GRADING_COMPLETED;
    }

    // 오답처리
    public void isWrong(){
        this.allCorrect = false;
        this.participationStatus = ParticipationStatus.GRADING_COMPLETED;
    }

    // 재시도 횟수 증가 처리
    public void increaseRetryCount(){
        this.retryCount++;
    }

    // 다시 제출 처리로 되돌리기
    public void markReSubmit(){
        this.participationStatus = ParticipationStatus.SUBMITTED;
    }

}
