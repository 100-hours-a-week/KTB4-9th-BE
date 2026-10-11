package com.cosmos.cosmos_backend.savedproblem.repository;

import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedProblemRepository extends JpaRepository<SavedProblem, Long> {

    // 사용자가 이 문제를 저장했는지 확인
    boolean existsByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자·문제의 저장 기록 삭제 (없으면 아무 일도 안 함)
    void deleteByUserIdAndProblemId(Long userId, Long problemId);

    // 사용자의 저장 문제를 최근 저장순으로 조회. difficulty가 null이면 전체
    @Query("""
            select new com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse(
                p.id, p.title, p.difficulty, p.category, s.savedAt)
            from SavedProblem s, Problem p
            where s.problemId = p.id
              and s.userId = :userId
              and (:difficulty is null or p.difficulty = :difficulty)
            order by s.savedAt desc
            """)
    List<SavedProblemResponse> findSavedProblems(
            @Param("userId") Long userId,
            @Param("difficulty") Difficulty difficulty
    );
}
