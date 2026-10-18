package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.DailyProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyProblemRepository extends JpaRepository<DailyProblem, Long> {
    // daily problem과 problem 패치 조인 적용
    @Query("""
    select dp
    from DailyProblem dp
    join fetch dp.problem
    where dp.recommendDate = :date
    order by dp.displayOrder desc
    """)
    List<DailyProblem> findWithProblemByRecommendDateOrderByDisplayOrderDesc(
            @Param("date") LocalDate date
    );


}
