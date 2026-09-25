package com.cosmos.cosmos_backend.ranking.domain.entity;


import com.cosmos.cosmos_backend.auth.domain.entity.User;
import com.cosmos.cosmos_backend.common.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_category_points", uniqueConstraints = {@UniqueConstraint(
        name = "uk_user_category_points",
        columnNames = {"user_id", "category"}
)})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCategoryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long UserCategoryPointId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "point", nullable = false)
    private Long point;

    @Column(name = "correct_problem_count", nullable = false)
    private Long correctProblemCount;

    public UserCategoryPoint(User user, Category category, Long point, Long correctProblemCount) {
        this.user = user;
        this.category = category;
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
