package com.cosmos.cosmos_backend.ranking.domain.entity;
import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.common.Difficulty;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_difficulty_points", uniqueConstraints = {@UniqueConstraint(
        name = "uk_user_difficulty_points",
        columnNames = {"user_id", "difficulty"}
)})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDifficultyPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long UserDifficultyPointId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty;

    @Column(name = "point", nullable = false)
    private Long point;

    @Column(name = "correct_problem_count", nullable = false)
    private Long correctProblemCount;

    public UserDifficultyPoint(User user, Difficulty difficulty, Long point, Long correctProblemCount) {
        this.user = user;
        this.difficulty = difficulty;
        this.point = point;
        this.correctProblemCount = correctProblemCount;
    }

    public void increasePoint(Long point) {
        this.point += point;
    }

    public void increaseCorrectProblemCount() {
        this.correctProblemCount++;
    }
}
