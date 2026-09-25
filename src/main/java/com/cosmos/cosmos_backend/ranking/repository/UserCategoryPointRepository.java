package com.cosmos.cosmos_backend.ranking.repository;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserCategoryPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCategoryPointRepository extends JpaRepository<UserCategoryPoint, Integer> {
    Optional<UserCategoryPoint> findByUser_IdAndCategory(Long userId, Category category);

    List<UserCategoryPoint> findTop100ByCategoryOrderByPointDesc(Category category);

    long countByCategoryAndPointGreaterThan(Category category, Long point);
}
