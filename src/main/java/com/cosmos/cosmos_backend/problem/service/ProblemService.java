package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.*;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemExampleRepository problemExampleRepository;
    private final RunningLimitRepository runningLimitRepository;
    private final HintRepository hintRepository;
    private final KeywordRepository keywordRepository;
    private final TestCaseRepository testCaseRepository;
    private final UsedHintRepository usedHintRepository;

    /** 문제 상세 조회. */
    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemDetail(Long userId, Long problemId) {
        // 1. problemId로 문제를 조회 (없으면 404 예외를 던짐, constraints는 Hibernate가 JSON에서 자동 변환)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 2. 사용자의 힌트 사용 단계를 조회 (기록이 없으면 0)
        int usedHintStage = usedHintRepository.findByUserIdAndProblemId(userId, problemId)
                .map(UsedHint::getHintStage)
                .orElse(0);

        // 3. 예시, 실행 제한을 각각 조회
        // 4. 조회한 값들을 응답 형태로 조립해서 반환
        return ProblemDetailResponse.of(
                problem,
                problemExampleRepository.findByProblemIdOrderByDisplayOrder(problemId),
                runningLimitRepository.findByProblemId(problemId),
                usedHintStage
        );
    }


    // ai -> BE 생성 문제 저장 요청
    @Transactional
    public List<Problem> createProblems(AiProblemsCreateRequestDto problemsCreateRequest) {

        List<AiProblemsCreateRequestDto.ProblemsInfo> aiProblems = problemsCreateRequest.aiProblems();

        // 필요한 엔티티 선언

        Problem problem;

        ProblemExample problemExample;

        RunningLimit runningLimit;

        TestCase testCase;

        Keyword keyword;

        Hint hintComment;

        Hint hintSolution;

        // 반환용 문제 리스트
        List<Problem> savedProblems = new ArrayList<>();

        for (AiProblemsCreateRequestDto.ProblemsInfo problemsInfo : aiProblems) {

            problem = new Problem(
                    problemsInfo.difficulty(),
                    problemsInfo.category(),
                    problemsInfo.problemTitle(),
                    problemsInfo.problemDescription(),
                    problemsInfo.inputFormat(),
                    problemsInfo.outputFormat(),
                    problemsInfo.inputConstraints(),
                    problemsInfo.categorySelectReason()
            );

            problemRepository.save(problem);

            //문제 id 추출
            Long problemId = problem.getId();

            // 반환용 문제 저장
            savedProblems.add(problem);

            // 입출력 예시
            for (int j = 0; j < problemsInfo.problemExamples().size(); j++) {
                problemExample = new ProblemExample(
                        problemId,
                        problemsInfo.problemExamples().get(j).input(),
                        problemsInfo.problemExamples().get(j).output(),
                        problemsInfo.problemExamples().get(j).description(),
                        j + 1
                );

                problemExampleRepository.save(problemExample);

            }

            // 언어별 정보 저장, 실행 조건, 힌트
            for(int k = 0 ; k < problemsInfo.executionLimit().size() ; k ++){
                runningLimit = new RunningLimit(
                        problemId,
                        problemsInfo.executionLimit().get(k).language(),
                        problemsInfo.executionLimit().get(k).timeLimitMs(),
                        problemsInfo.executionLimit().get(k).memoryLimitKb()
                );

                runningLimitRepository.save(runningLimit);

                hintComment = new Hint(
                        problemId,
                        problemsInfo.hintComments().get(k).language(),
                        HintType.COMMENT,
                        problemsInfo.hintComments().get(k).content()
                );

                hintRepository.save(hintComment);

                hintSolution = new Hint(
                        problemId,
                        problemsInfo.hintSolutionCodes().get(k).language(),
                        HintType.SOLUTION,
                        problemsInfo.hintSolutionCodes().get(k).content()
                );

                hintRepository.save(hintSolution);
            }

            // 코드 채점용 테스트 케이스
            for (int l = 0 ; l < problemsInfo.hiddenTests().size() ; l ++){
                testCase = new TestCase(
                        problemId,
                        problemsInfo.hiddenTests().get(l).input(),
                        problemsInfo.hiddenTests().get(l).output(),
                        l + 1
                );

                testCaseRepository.save(testCase);
            }

            // 자연어 문제 풀이용 키퉈드
            for (int m = 0 ; m < problemsInfo.solutionKeywords().size() ; m++){
                keyword = new Keyword(
                        problemId,
                        problemsInfo.solutionKeywords().get(m)
                );

                keywordRepository.save(keyword);
            }
        }

        return savedProblems;
    }
}
