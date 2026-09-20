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
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        Category category = parseCategory(selectedCategory);
        boolean categoryResult = category.name().equals(problem.getCategory());

        return approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId)
                .map(existing -> {
                    existing.resubmit(category, approach, categoryResult);
                    return existing;
                })
                .orElseGet(() -> approachSubmissionRepository.save(
                        new ApproachSubmission(userId, problemId, category, approach, categoryResult)
                ));
    }

    private Category parseCategory(String value) {
        try {
            return Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_selected_category");
        }
    }
}
