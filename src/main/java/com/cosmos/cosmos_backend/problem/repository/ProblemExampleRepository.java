package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemExampleRepository extends JpaRepository<ProblemExample, Long> {

    // 문제 id로 공개 예시 목록을 display_order 순으로 조회
    List<ProblemExample> findByProblemIdOrderByDisplayOrder(Long problemId);
}
