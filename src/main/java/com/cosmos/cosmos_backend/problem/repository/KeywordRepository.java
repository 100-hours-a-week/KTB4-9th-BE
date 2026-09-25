package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.Keyword;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    // 문제 id로 핵심 키워드 목록 조회 (등록 순서)
    List<Keyword> findByProblemIdOrderById(Long problemId);
}
