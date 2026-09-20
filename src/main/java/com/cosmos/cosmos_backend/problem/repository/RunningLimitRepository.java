package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.RunningLimit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunningLimitRepository extends JpaRepository<RunningLimit, Long> {

    // 문제 id로 언어별 실행 제한 목록 조회
    List<RunningLimit> findByProblemId(Long problemId);
}
