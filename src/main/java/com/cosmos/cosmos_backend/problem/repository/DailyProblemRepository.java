package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.entity.DailyProblem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyProblemRepository extends JpaRepository<DailyProblem, Long> {
    List<DailyProblem> findByRecommendDateOrderByDisplayOrderDesc(LocalDate date);
}
