package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.client.AiProblemClient;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.*;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.AiProblemCreateOndemandResponseDto;
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

    private final AiProblemClient aiProblemClient;

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

    // 온디맨드 문제 생성
    @Transactional
    public Problem createOnDemandProblem(
            Difficulty difficulty,
            Category category
    ) {

        // 1. AI 서버에 문제 생성 요청
        AiProblemCreateOndemandResponseDto response =
                aiProblemClient.createProblem(difficulty, category);

        // 2. AI 응답 검증
        if (response == null || !response.success() || response.problem() == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ai_problem_creation_failed");
        }

        // 3. AI 응답 → 기존 저장 DTO 변환
        AiProblemsCreateRequestDto.ProblemsInfo problemsInfo = convertToProblemsInfo(response.problem());

        // 4. DB 저장
        return saveProblem(problemsInfo);
    }


    // ai -> BE 생성 문제 저장 요청
    @Transactional
    public List<Problem> createProblems(
            AiProblemsCreateRequestDto problemsCreateRequest
    ) {

        List<Problem> savedProblems = new ArrayList<>();

        for (AiProblemsCreateRequestDto.ProblemsInfo problemsInfo : problemsCreateRequest.aiProblems()) {
            savedProblems.add(saveProblem(problemsInfo));
        }

        return savedProblems;
    }


    // 문제 저장 로직 분리
    private Problem saveProblem(
            AiProblemsCreateRequestDto.ProblemsInfo problemsInfo
    ) {

        // 1. 문제 저장
        Problem problem = new Problem(
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

        Long problemId = problem.getId();


        // 2. 입출력 예시 저장
        for (int i = 0; i < problemsInfo.problemExamples().size(); i++) {

            AiProblemsCreateRequestDto.ProblemExamples example =
                    problemsInfo.problemExamples().get(i);

            ProblemExample problemExample = new ProblemExample(
                    problemId,
                    example.input(),
                    example.output(),
                    example.description(),
                    i + 1
            );

            problemExampleRepository.save(problemExample);
        }


        // 3. 언어별 실행 제한 + 힌트 저장
        for (int i = 0; i < problemsInfo.executionLimit().size(); i++) {

            AiProblemsCreateRequestDto.ExecutionLimits limit =
                    problemsInfo.executionLimit().get(i);

            RunningLimit runningLimit = new RunningLimit(
                    problemId,
                    limit.language(),
                    limit.timeLimitMs(),
                    limit.memoryLimitKb()
            );

            runningLimitRepository.save(runningLimit);


            AiProblemsCreateRequestDto.HintComments comment =
                    problemsInfo.hintComments().get(i);

            Hint hintComment = new Hint(
                    problemId,
                    comment.language(),
                    HintType.COMMENT,
                    comment.content()
            );

            hintRepository.save(hintComment);


            AiProblemsCreateRequestDto.HintSolutionCodes solution =
                    problemsInfo.hintSolutionCodes().get(i);

            Hint hintSolution = new Hint(
                    problemId,
                    solution.language(),
                    HintType.SOLUTION,
                    solution.content()
            );

            hintRepository.save(hintSolution);
        }


        // 4. 테스트 케이스 저장
        for (int i = 0; i < problemsInfo.hiddenTests().size(); i++) {

            AiProblemsCreateRequestDto.HiddenTestCases hiddenTest =
                    problemsInfo.hiddenTests().get(i);

            TestCase testCase = new TestCase(
                    problemId,
                    hiddenTest.input(),
                    hiddenTest.output(),
                    i + 1
            );

            testCaseRepository.save(testCase);
        }


        // 5. 자연어 풀이 키워드 저장
        for (String solutionKeyword : problemsInfo.solutionKeywords()) {

            Keyword keyword = new Keyword(
                    problemId,
                    solutionKeyword
            );

            keywordRepository.save(keyword);
        }


        // 6. 저장한 문제 반환
        return problem;
    }


    // 온디멘드 문제 생성 dto 변환
    private AiProblemsCreateRequestDto.ProblemsInfo convertToProblemsInfo(
            AiProblemCreateOndemandResponseDto.ProblemInfo problem
    ) {

        return new AiProblemsCreateRequestDto.ProblemsInfo(

                problem.problemTitle(),

                // problemContent → problemDescription
                problem.problemContent(),

                problem.inputFormat(),
                problem.outputFormat(),
                problem.difficulty(),
                problem.category(),
                problem.categorySelectReason(),
                problem.solutionKeywords(),

                // examples
                problem.problemExamples().stream()
                        .map(example ->
                                new AiProblemsCreateRequestDto.ProblemExamples(
                                        example.input(),
                                        example.output(),
                                        example.description()
                                )
                        )
                        .toList(),

                // constraints
                problem.inputConstraints().stream()
                        .map(constraint ->
                                new AiProblemsCreateRequestDto.InputConstraints(
                                        constraint.target(),
                                        constraint.scope(),
                                        constraint.dataType(),
                                        constraint.minValue(),
                                        constraint.maxValue(),
                                        constraint.specialConditions()
                                )
                        )
                        .toList(),

                // execution limits
                problem.executionLimits().stream()
                        .map(limit ->
                                new AiProblemsCreateRequestDto.ExecutionLimits(
                                        limit.language(),
                                        limit.timeLimitMs(),
                                        limit.memoryLimitKb()
                                )
                        )
                        .toList(),

                // hidden test cases
                problem.hiddenTestCases().stream()
                        .map(test ->
                                new AiProblemsCreateRequestDto.HiddenTestCases(
                                        test.input(),
                                        test.output()
                                )
                        )
                        .toList(),

                // comment → content
                problem.hintComments().stream()
                        .map(comment ->
                                new AiProblemsCreateRequestDto.HintComments(
                                        comment.language(),
                                        comment.comment()
                                )
                        )
                        .toList(),

                // code → content
                problem.solutionCodes().stream()
                        .map(solution ->
                                new AiProblemsCreateRequestDto.HintSolutionCodes(
                                        solution.language(),
                                        solution.code()
                                )
                        )
                        .toList()
        );
    }
}
