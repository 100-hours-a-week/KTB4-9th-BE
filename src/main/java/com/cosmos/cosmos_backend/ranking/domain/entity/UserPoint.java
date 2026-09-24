package com.cosmos.cosmos_backend.ranking.domain.entity;

import com.cosmos.cosmos_backend.auth.domain.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_points", uniqueConstraints = {@UniqueConstraint(
        name = "uk_user_points",
        columnNames = {"user_id"}
)})
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long userPointId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "total_point", nullable = false)
    private Long totalPoint;

    @Column(name = "total_correct_problem_count", nullable = false)
    private Long totalCorrectProblemCount;

    @Column(name = "current_correct_streak", nullable = false)
    private Long currentStreakDay;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserPoint(User user, Long totalPoint, Long totalCorrectProblemCount, Long currentCorrectStreak) {
        this.user = user;
        this.totalPoint = totalPoint;
        this.totalCorrectProblemCount = totalCorrectProblemCount;
        this.currentStreakDay = currentCorrectStreak;
    }

    // 현재 연속 정답 일수 +1
    public void increaseCorrectStreak() {
        this.currentStreakDay++;
    }

    // 누적 정답 수 + 1
    public void increaseCorrectProblemCount() {
        this.totalCorrectProblemCount++;
    }

}
