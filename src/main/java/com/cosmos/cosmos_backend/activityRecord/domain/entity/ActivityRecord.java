package com.cosmos.cosmos_backend.activityRecord.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "activities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activities_user_activity_date",
                columnNames = {"user_id", "activity_date"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ActivityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "correct_problem_count", nullable = false)
    private Long correctProblemCount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public  ActivityRecord(Long userId, LocalDate activityDate, Long correctProblemCount) {
        this.userId = userId;
        this.activityDate = activityDate;
        this.correctProblemCount = correctProblemCount;
    }

    // 정답 수 증가
    public void increaseCorrectProblemCount() {
        this.correctProblemCount++;
    }

}
