package com.cosmos.cosmos_backend.savedproblem.repository;

import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.dto.SavedProblemRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedProblemRepository extends JpaRepository<SavedProblem, Long> {

    // 사용자가 이 문제를 저장했는지 확인
    boolean existsByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자가 저장한 문제 행을 조회 (저장 당시 categoryKnown을 쓰기 위함)
    Optional<SavedProblem> findByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자·문제의 저장 기록 삭제 (없으면 아무 일도 안 함)
    void deleteByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자의 저장 문제를 최근 저장순으로 조회. difficulty가 null이면 전체
    @Query("""
            select new com.cosmos.cosmos_backend.savedproblem.dto.SavedProblemRow(
                p.id, p.title, p.difficulty, p.category, s.savedAt, a.isSolved, a.naturalSolution,
                s.categoryKnown, a.categoryResult, a.submittedCount)
            from SavedProblem s
            join Problem p on p.id = s.problemId
            left join ApproachSubmission a on a.userId = s.userId and a.problemId = s.problemId
            where s.userId = :userId
              and (:difficulty is null or p.difficulty = :difficulty)
            order by s.savedAt desc
            """)
    List<SavedProblemRow> findSavedProblems(
            @Param("userId") Long userId,
            @Param("difficulty") Difficulty difficulty
    );
}
