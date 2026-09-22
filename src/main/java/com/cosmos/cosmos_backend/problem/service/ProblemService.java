package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.Problem;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.repository.ProblemExampleRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemExampleRepository problemExampleRepository;
    private final RunningLimitRepository runningLimitRepository;

    /** 문제 상세 조회. */
    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemDetail(Long problemId) {
        // 1. problemId로 문제를 조회 (없으면 404 예외를 던짐, constraints는 Hibernate가 JSON에서 자동 변환)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 2. 예시, 실행 제한을 각각 조회
        // 3. 조회한 값들을 응답 형태로 조립해서 반환
        return ProblemDetailResponse.of(
                problem,
                problemExampleRepository.findByProblemIdOrderByDisplayOrder(problemId),
                runningLimitRepository.findByProblemId(problemId)
        );
    }
}
