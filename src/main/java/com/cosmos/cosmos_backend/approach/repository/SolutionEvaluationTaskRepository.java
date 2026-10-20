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

    // 워커의 작업 선점: 대기 중인 작업 중 가장 오래된 1건을 잠가서 조회 (다른 워커가 잠근 행은 건너뜀)
    @Query(value = """
            SELECT *
            FROM solution_evaluation_tasks
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<SolutionEvaluationTask> findNextForUpdate();
}
