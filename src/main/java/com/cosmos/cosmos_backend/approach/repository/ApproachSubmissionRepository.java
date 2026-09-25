package com.cosmos.cosmos_backend.approach.repository;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApproachSubmissionRepository extends JpaRepository<ApproachSubmission, Long> {

    // 사용자·문제별 제출 횟수를 숫자로만 조회 (엔티티를 영속성 컨텍스트에 올리지 않기 위해 값만 꺼냄)
    @Query("select s.submittedCount from ApproachSubmission s where s.userId = :userId and s.problemId = :problemId")
    Optional<Integer> findSubmittedCount(@Param("userId") Long userId, @Param("problemId") Long problemId);

    // 사용자·문제별 기존 제출을 잠그고 조회 (트랜잭션 안에서만 호출 가능)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApproachSubmission> findForUpdateByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자·문제별 기존 제출 조회 (재제출 여부 판단용)
    Optional<ApproachSubmission> findByUserIdAndProblemId(Long userId, Long problemId);
}
