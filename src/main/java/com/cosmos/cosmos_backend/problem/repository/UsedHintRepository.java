package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.UsedHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedHintRepository extends JpaRepository<UsedHint, Long> {

    // 사용자·문제별 힌트 사용 기록 조회
    Optional<UsedHint> findByUserIdAndProblemId(Long userId, Long problemId);
}
