package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.Hint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRepository extends JpaRepository<Hint,Long> {

    // 문제·언어·유형에 맞는 힌트 한 개 조회
    Optional<Hint> findByProblemIdAndLanguageAndHintType(Long problemId, Language language, HintType hintType);
}
