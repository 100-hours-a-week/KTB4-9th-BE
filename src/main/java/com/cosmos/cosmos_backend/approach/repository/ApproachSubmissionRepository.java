package com.cosmos.cosmos_backend.approach.repository;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApproachSubmissionRepository extends JpaRepository<ApproachSubmission, Long> {

    // 사용자·문제별 기존 제출 조회 (재제출 여부 판단용)
    Optional<ApproachSubmission> findByUserIdAndProblemId(Long userId, Long problemId);
}
