package com.cosmos.cosmos_backend.approach.repository;

import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolutionEvaluationTaskRepository extends JpaRepository<SolutionEvaluationTask, Long> {

    // 사용자·문제의 활성 작업 조회 (active_slot = 1인 행, 없으면 빈 값)
    @Query("select t from SolutionEvaluationTask t where t.userId = :userId and t.problemId = :problemId and t.activeSlot = 1")
    Optional<SolutionEvaluationTask> findActive(@Param("userId") Long userId, @Param("problemId") Long problemId);

    // 사용자에게 진행 중인 평가 작업(active_slot이 있는 작업)이 있는지
    boolean existsByUserIdAndActiveSlot(Long userId, Integer activeSlot);

    // 작업 id와 소유자로 조회 (남의 작업은 조회되지 않음)
    Optional<SolutionEvaluationTask> findByIdAndUserId(Long id, Long userId);

    // 같은 사용자·문제의 가장 최근 작업 (id가 가장 큰 것)
    Optional<SolutionEvaluationTask> findFirstByUserIdAndProblemIdOrderByIdDesc(Long userId, Long problemId);

    // 사용자가 해당 문제에 접수한 평가 작업이 있는지 (진행 중, 실패, 성공 모두)
    boolean existsByUserIdAndProblemId(Long userId, Long problemId);

    // 워커의 작업 선점: 대기 중이거나 재시도 시각이 지난 작업 중 가장 오래된 1건을 잠가서 조회 (다른 워커가 잠근 행은 건너뜀)
    @Query(value = """
            SELECT *
            FROM solution_evaluation_tasks
            WHERE status = 'PENDING'
               OR (status = 'RETRY_WAIT' AND next_retry_at <= :now)
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<SolutionEvaluationTask> findNextForUpdate(@Param("now") LocalDateTime now);

    // 작업을 잠그고 조회 (결과 저장·실패 처리·복구가 같은 작업을 동시에 바꾸지 않도록, 트랜잭션 안에서만 호출 가능)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SolutionEvaluationTask> findForUpdateById(Long id);

    // 기준 시각 이전에 시작했는데 아직 PROCESSING인 작업의 id 조회
    @Query("select t.id from SolutionEvaluationTask t where t.status = com.cosmos.cosmos_backend.approach.domain.EvaluationTaskStatus.PROCESSING and t.startedAt < :threshold")
    List<Long> findStuckIds(@Param("threshold") LocalDateTime threshold);
}
