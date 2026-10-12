package com.cosmos.cosmos_backend.savedproblem.service;

import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.dto.response.SavedProblemResponse;
import com.cosmos.cosmos_backend.savedproblem.repository.SavedProblemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavedProblemService {

    private final SavedProblemRepository savedProblemRepository;
    private final ProblemRepository problemRepository;

    /** 문제 저장. 새로 저장했으면 true, 이미 저장돼 있었으면 false. */
    @Transactional
    public boolean save(Long userId, Long problemId) {
        // 1. 문제가 있는지 확인 (없으면 404 예외를 던짐)
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 2. 이미 저장돼 있으면 새로 만들지 않고 false를 반환
        if (savedProblemRepository.existsByUserIdAndProblemId(userId, problemId)) {
            return false;
        }

        // 3. 새로 저장하고 true를 반환
        savedProblemRepository.save(new SavedProblem(userId, problemId));
        return true;
    }

    /** 저장 해제. 저장돼 있지 않아도 에러 없이 끝남. */
    @Transactional
    public void unsave(Long userId, Long problemId) {
        savedProblemRepository.deleteByUserIdAndProblemId(userId, problemId);
    }

    /** 저장 문제 목록 조회. difficulty가 null이면 전체. */
    @Transactional(readOnly = true)
    public List<SavedProblemResponse> getSavedProblems(Long userId, Difficulty difficulty) {
        return savedProblemRepository.findSavedProblems(userId, difficulty).stream()
                .map(SavedProblemResponse::from)
                .toList();
    }
}
