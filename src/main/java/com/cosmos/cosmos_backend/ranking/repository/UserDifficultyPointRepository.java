package com.cosmos.cosmos_backend.ranking.repository;

import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserDifficultyPoint;
import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDifficultyPointRepository extends JpaRepository<UserDifficultyPoint, Integer> {

    Optional<UserDifficultyPoint> findByUser_IdAndDifficulty(Long userId, Difficulty difficulty);

    List<UserDifficultyPoint> findTop100ByDifficultyAndPointGreaterThanOrderByPointDesc(Difficulty difficulty, Long point);

    long countByDifficultyAndPointGreaterThan(Difficulty  difficulty, Long point);
}
