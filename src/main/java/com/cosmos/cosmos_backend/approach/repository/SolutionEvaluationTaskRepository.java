package com.cosmos.cosmos_backend.approach.repository;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolutionEvaluationTaskRepository extends JpaRepository<SolutionEvaluationTask, Long> {

    // 사용자·문제의 활성 작업 조회 (active_slot = 1인 행, 없으면 빈 값)
    @Query("select t from SolutionEvaluationTask t where t.userId = :userId and t.problemId = :problemId and t.activeSlot = 1")
    Optional<SolutionEvaluationTask> findActive(@Param("userId") Long userId, @Param("problemId") Long problemId);
}
