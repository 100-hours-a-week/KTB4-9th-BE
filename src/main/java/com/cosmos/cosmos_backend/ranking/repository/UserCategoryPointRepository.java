package com.cosmos.cosmos_backend.ranking.repository;

import com.cosmos.cosmos_backend.ranking.domain.entity.UserCategoryPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCategoryPointRepository extends JpaRepository<UserCategoryPoint, Integer> {
    Optional<UserPoint> findByUser_UserId(Long userId);

    List<UserPoint> findTop100ByOrderByPointDesc();

    long countByPointGreaterThan(Long point);
}
