package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemExampleRepository extends JpaRepository<ProblemExample, Long> {

    // 문제 id로 공개 예시 목록을 display_order 순으로 조회
    List<ProblemExample> findByProblemIdOrderByDisplayOrder(Long problemId);

    // 문제 id를 모아서 당일 데일리 문제 id인 문제 예시들 한 번에 조회(IN 쿼리)
    List<ProblemExample> findByProblemIdInOrderByProblemIdAscDisplayOrderAsc(List<Long> problemIds);

}