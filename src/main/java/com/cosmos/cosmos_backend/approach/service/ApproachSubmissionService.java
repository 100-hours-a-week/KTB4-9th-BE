package com.cosmos.cosmos_backend.approach.service;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.Problem;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApproachSubmissionService {

    private final ProblemRepository problemRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;

    @Transactional
    public ApproachSubmission submit(Long userId, Long problemId, String selectedCategory, String approach) {
        // 1. problemId로 문제를 조회 (없으면 404 예외를 던짐)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 2. 선택한 카테고리를 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Category category = parseCategory(selectedCategory);

        // 3. 선택한 카테고리와 문제의 정답 카테고리를 비교해 정답 여부를 계산
        boolean categoryResult = category.name().equals(problem.getCategory());

        // 4. 이미 제출한 적이 있으면 갱신(재제출), 없으면 새로 저장
        return approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId)
                .map(existing -> {
                    existing.resubmit(category, approach, categoryResult);
                    return existing;
                })
                .orElseGet(() -> approachSubmissionRepository.save(
                        new ApproachSubmission(userId, problemId, category, approach, categoryResult)
                ));
    }

    // 카테고리 문자열을 Category enum으로 변환
    private Category parseCategory(String value) {
        try {
            // 1. 문자열을 enum으로 변환
            return Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 2. 없는 값이면 400 예외를 던짐
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_selected_category");
        }
    }
}
