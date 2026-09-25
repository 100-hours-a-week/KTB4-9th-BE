package com.cosmos.cosmos_backend.ranking.repository;

import com.cosmos.cosmos_backend.ranking.domain.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface UserPointRepository extends JpaRepository<UserPoint, Long> {
    Optional<UserPoint> findByUser_Id(Long userId);

    List<UserPoint> findTop100ByTotalPointGreaterThanOrderByTotalPointDesc(Long point);;

    long countByTotalPointGreaterThan(Long point);

}
