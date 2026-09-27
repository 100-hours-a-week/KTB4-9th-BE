package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.TestCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    // 문제의 테스트케이스를 노출 순서대로 조회
    List<TestCase> findByProblemIdOrderByDisplayOrder(Long problemId);
}
