package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.response.ActiveEvaluationResponse;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.codesubmission.repository.CodeSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.client.AiProblemClient;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.*;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.AiProblemCreateOndemandResponseDto;
import com.cosmos.cosmos_backend.problem.dto.response.MyApproachResponse;
import com.cosmos.cosmos_backend.problem.dto.response.MyCodeResponse;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.repository.*;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.repository.SavedProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final SavedProblemRepository savedProblemRepository;
    private final DailyProblemRepository dailyProblemRepository;
    private final DailyGeneratedCountRepository dailyGeneratedCountRepository;
    private final ApproachSubmissionRepository approachSubmissionRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final SolutionEvaluationTaskRepository solutionEvaluationTaskRepository;

    private final AiProblemClient aiProblemClient;
    private final Clock clock;

    /** 문제 상세 조회. */
    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemDetail(Long userId, Long problemId) {
        // 1. problemId로 문제를 조회 (없으면 404 예외를 던짐, constraints는 Hibernate가 JSON에서 자동 변환)
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // 2. 내가 저장한 문제인지 조회 (저장할 때 기록한 categoryKnown을 쓰려고 행 자체를 가져옴)
        Optional<SavedProblem> savedProblem = savedProblemRepository.findByUserIdAndProblemId(userId, problemId);
        boolean saved = savedProblem.isPresent();

        // 3. 열 수 있는 문제인지 확인 (저장한 문제, 오늘의 문제, 오늘 마지막으로 생성한 문제, 내가 풀이를 제출한 적 있는 문제만 허용, 아니면 404 예외를 던짐)
        boolean todayDaily = dailyProblemRepository.existsByProblemIdAndRecommendDate(problemId, LocalDate.now(clock));
        if (!canOpen(userId, problemId, saved, todayDaily)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 4. 사용자의 힌트 사용 단계를 조회 (기록이 없으면 0)
        int usedHintStage = usedHintRepository.findByUserIdAndProblemId(userId, problemId)
                .map(UsedHint::getHintStage)
                .orElse(0);

        // 5. 내가 이전에 제출한 접근 방식과 코드를 조회 (없으면 null)
        Optional<ApproachSubmission> approachSubmission =
                approachSubmissionRepository.findByUserIdAndProblemId(userId, problemId);
        MyApproachResponse myApproach = approachSubmission.map(submission -> MyApproachResponse.from(submission, problem)).orElse(null);
        MyCodeResponse myCode = codeSubmissionRepository.findByUserIdAndProblemId(userId, problemId)
                .map(MyCodeResponse::from)
                .orElse(null);

        // 5-1. 지금 진행 중인 자연어 풀이 평가 작업을 조회 (없으면 null)
        ActiveEvaluationResponse activeEvaluation = solutionEvaluationTaskRepository.findActive(userId, problemId)
                .map(ActiveEvaluationResponse::from)
                .orElse(null);

        // 6. 카테고리 공개 여부를 정함 (숨기지 않으면 카테고리 맞추기가 의미가 없어짐)
        //   - 카테고리를 알고 있던 문제: 저장했다면 저장 당시 값, 저장 안 했다면 오늘의 문제일 때
        //   - 카테고리를 맞춘 문제, 또는 제출 한도를 다 쓴 문제
        boolean categoryKnown = savedProblem.map(SavedProblem::isCategoryKnown)
                .orElse(todayDaily);
        boolean categoryRevealed = approachSubmission
                .map(submission -> Boolean.TRUE.equals(submission.getCategoryResult())
                        || submission.getSubmittedCount() >= ApproachSubmission.MAX_SUBMISSION_COUNT)
                .orElse(false);
        Category visibleCategory = (categoryKnown || categoryRevealed) ? problem.getCategory() : null;

        // 7. 예시, 실행 제한을 각각 조회
        // 8. 조회한 값들을 응답 형태로 조립해서 반환
        return ProblemDetailResponse.of(
                problem,
                problemExampleRepository.findByProblemIdOrderByDisplayOrder(problemId),
                runningLimitRepository.findByProblemId(problemId),
                visibleCategory,
                usedHintStage,
                saved,
                myApproach,
                myCode,
                activeEvaluation
        );
    }

    /** 사용자가 열 수 있는 문제인지. 풀이 접수 때도 같은 규칙을 쓰려고 밖에 공개함. */
    @Transactional(readOnly = true)
    public boolean canOpen(Long userId, Long problemId) {
        // 1. 저장했는지, 오늘의 문제인지 조회해서 아래 판단에 넘김
        boolean saved = savedProblemRepository.findByUserIdAndProblemId(userId, problemId).isPresent();
        boolean todayDaily = dailyProblemRepository.existsByProblemIdAndRecommendDate(problemId, LocalDate.now(clock));
        return canOpen(userId, problemId, saved, todayDaily);
    }

    // 이미 조회한 저장·오늘의 문제 여부를 받아서 열 수 있는지 판단 (같은 조회를 두 번 하지 않으려고 나눔)
    private boolean canOpen(Long userId, Long problemId, boolean saved, boolean todayDaily) {
        // 1. 가볍게 알 수 있는 조건부터 확인
        if (saved || todayDaily) {
            return true;
        }

        // 2. 내가 풀이를 제출한 적이 있는 문제면 열 수 있음 (제출했다면 그때 열 수 있었던 문제)
        if (approachSubmissionRepository.existsByUserIdAndProblemId(userId, problemId)
                || solutionEvaluationTaskRepository.existsByUserIdAndProblemId(userId, problemId)) {
            return true;
        }

        // 3. 오늘 마지막으로 생성한 문제인지 확인
        return dailyGeneratedCountRepository.findLastProblemId(userId, LocalDate.now(clock))
                .filter(problemId::equals)
                .isPresent();
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
                                        comment.content()
                                )
                        )
                        .toList(),

                // code → content
                problem.solutionCodes().stream()
                        .map(solution ->
                                new AiProblemsCreateRequestDto.HintSolutionCodes(
                                        solution.language(),
                                        solution.content()
                                )
                        )
                        .toList()
        );
    }
}
