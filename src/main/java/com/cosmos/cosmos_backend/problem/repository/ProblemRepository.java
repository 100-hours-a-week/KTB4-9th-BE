package com.cosmos.cosmos_backend.problem.repository;

import com.cosmos.cosmos_backend.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
