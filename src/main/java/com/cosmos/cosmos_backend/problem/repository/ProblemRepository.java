package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    // 난이도(+카테고리)에 맞고, 이 사용자가 풀이를 제출한 적 없는 문제를 랜덤으로 1건 조회 (category가 null이면 카테고리 무관)
    @Query(value = """
            SELECT p.* FROM problems p
            WHERE p.difficulty = :difficulty
              AND (:category IS NULL OR p.category = :category)
              AND NOT EXISTS (SELECT 1 FROM problem_solution_submissions s
                              WHERE s.problem_id = p.id AND s.user_id = :userId)
            ORDER BY RAND()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Problem> findRandomUnsolved(@Param("difficulty") String difficulty,
                                         @Param("category") String category,
                                         @Param("userId") Long userId);

    // 아무도 풀이를 제출한 적 없는 문제를 난이도·카테고리별로 센다
    @Query(value = """
            SELECT p.difficulty AS difficulty, p.category AS category, COUNT(*) AS problemCount
            FROM problems p
            WHERE NOT EXISTS (SELECT 1 FROM problem_solution_submissions s WHERE s.problem_id = p.id)
            GROUP BY p.difficulty, p.category
            """, nativeQuery = true)
    List<ProblemCountRow> countUnsolvedGroupByDifficultyAndCategory();

    interface ProblemCountRow {
        String getDifficulty();

        String getCategory();

        Long getProblemCount();
    }
}
