package com.cosmos.cosmos_backend.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.domain.KeywordResult;
import com.cosmos.cosmos_backend.approach.domain.SolutionEvaluationTask;
import com.cosmos.cosmos_backend.approach.dto.response.ApproachSubmitResponse;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.approach.repository.SolutionEvaluationTaskRepository;
import com.cosmos.cosmos_backend.codesubmission.domain.CodeSubmission;
import com.cosmos.cosmos_backend.codesubmission.domain.JudgingResult;
import com.cosmos.cosmos_backend.codesubmission.repository.CodeSubmissionRepository;
import com.cosmos.cosmos_backend.common.*;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.client.AiProblemClient;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import com.cosmos.cosmos_backend.problem.domain.entity.RunningLimit;
import com.cosmos.cosmos_backend.problem.domain.entity.UsedHint;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.repository.*;
import com.cosmos.cosmos_backend.savedproblem.domain.SavedProblem;
import com.cosmos.cosmos_backend.savedproblem.repository.SavedProblemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    // 서버 시계는 KST 2026-10-19 10:00으로 고정
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemExampleRepository problemExampleRepository;

    @Mock
    private RunningLimitRepository runningLimitRepository;

    @Mock
    private HintRepository hintRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private UsedHintRepository usedHintRepository;

    @Mock
    private SavedProblemRepository savedProblemRepository;

    @Mock
    private DailyProblemRepository dailyProblemRepository;

    @Mock
    private DailyGeneratedCountRepository dailyGeneratedCountRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;

    @Mock
    private SolutionEvaluationTaskRepository solutionEvaluationTaskRepository;

    @Mock
    private AiProblemClient aiProblemClient;

    private ProblemService service() {
        return new ProblemService(problemRepository, problemExampleRepository, runningLimitRepository, hintRepository, keywordRepository, testCaseRepository, usedHintRepository, savedProblemRepository, dailyProblemRepository, dailyGeneratedCountRepository, approachSubmissionRepository, codeSubmissionRepository, solutionEvaluationTaskRepository, aiProblemClient, CLOCK);
    }

    @Test
    void getProblemDetail_returnsActiveEvaluation_whenEvaluationIsInProgress() {
        // Given: 평가가 진행 중인 사용자
        stubProblemForCategoryRule();
        givenLastGenerated(1L);
        SolutionEvaluationTask task = new SolutionEvaluationTask(7L, 1L, Category.BINARY_SEARCH, "중간값을 확인합니다", LocalDateTime.of(2026, 10, 19, 3, 30, 0));
        task.startProcessing(LocalDateTime.of(2026, 10, 19, 3, 30, 1));
        when(solutionEvaluationTaskRepository.findActive(7L, 1L)).thenReturn(Optional.of(task));

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then
        assertThat(response.activeEvaluation()).isNotNull();
        assertThat(response.activeEvaluation().status()).isEqualTo("PROCESSING");
        assertThat(response.activeEvaluation().selectedCategory()).isEqualTo("BINARY_SEARCH");
        assertThat(response.activeEvaluation().naturalSolution()).isEqualTo("중간값을 확인합니다");
        assertThat(response.activeEvaluation().createdAt()).isEqualTo(Instant.parse("2026-10-19T03:30:00Z"));
        assertThat(response.activeEvaluation().startedAt()).isEqualTo(Instant.parse("2026-10-19T03:30:01Z"));
        assertThat(response.activeEvaluation().nextRetryAt()).isNull();
    }

    @Test
    void getProblemDetail_returnsNullActiveEvaluation_whenNothingIsInProgress() {
        // Given
        stubProblemForCategoryRule();
        givenLastGenerated(1L);
        when(solutionEvaluationTaskRepository.findActive(7L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).activeEvaluation()).isNull();
    }

    // 오늘 마지막으로 생성한 문제가 problemId인 상태를 만든다. (접근 확인을 통과시키려고)
    private void givenLastGenerated(Long problemId) {
        when(dailyGeneratedCountRepository.findLastProblemId(7L, TODAY)).thenReturn(Optional.of(problemId));
    }

    // 카테고리 공개 규칙 테스트용: 1번 문제(ARRAY)와 기본 조회 결과를 준비
    private void stubProblemForCategoryRule() {
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.empty());
    }

    @Test
    void getProblemDetail_showsCategory_whenUnsavedDailyProblem() {
        // Given: 저장하지 않은 데일리 문제
        stubProblemForCategoryRule();
        when(dailyProblemRepository.existsByProblemIdAndRecommendDate(1L, TODAY)).thenReturn(true);

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isEqualTo(Category.ARRAY);
    }

    @Test
    void getProblemDetail_showsCategory_whenSavedWithCategoryKnown() {
        // Given: 카테고리를 알고 저장한 문제
        stubProblemForCategoryRule();
        when(savedProblemRepository.findByUserIdAndProblemId(7L, 1L))
                .thenReturn(Optional.of(new SavedProblem(7L, 1L, true)));

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isEqualTo(Category.ARRAY);
    }

    @Test
    void getProblemDetail_hidesCategory_whenSavedRandomProblemEvenIfDaily() {
        // Given: 과거 데일리 문제를 랜덤으로 뽑아 저장한 경우 (저장 기록이 데일리 여부보다 우선)
        stubProblemForCategoryRule();
        when(savedProblemRepository.findByUserIdAndProblemId(7L, 1L))
                .thenReturn(Optional.of(new SavedProblem(7L, 1L, false)));

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isNull();
    }

    @Test
    void getProblemDetail_hidesCategory_whenWrongCategoryAndLimitRemains() {
        // Given: 랜덤 문제에서 카테고리를 틀렸고 제출 한도가 남은 상태
        stubProblemForCategoryRule();
        givenLastGenerated(1L);
        when(approachSubmissionRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(
                new ApproachSubmission(7L, 1L, Category.DP, "틀린 풀이", false, 30, "아쉬워요")
        ));

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isNull();
    }

    @Test
    void getProblemDetail_showsCategory_whenWrongCategoryButLimitReached() {
        // Given: 카테고리를 계속 틀려서 제출 한도(5회)를 다 쓴 상태
        stubProblemForCategoryRule();
        givenLastGenerated(1L);
        ApproachSubmission approach = new ApproachSubmission(7L, 1L, Category.DP, "틀린 풀이", false, 30, "아쉬워요");
        for (int i = 1; i < ApproachSubmission.MAX_SUBMISSION_COUNT; i++) {
            approach.resubmit(Category.DP, "틀린 풀이", false, 30, "아쉬워요");
        }
        when(approachSubmissionRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(approach));

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isEqualTo(Category.ARRAY);
    }

    @Test
    void getProblemDetail_returnsAssembledResponse_whenProblemExists() {
        // Given
        List<AiProblemsCreateRequestDto.InputConstraints> constraints = List.of(
                new AiProblemsCreateRequestDto.InputConstraints("nums.length", Scope.INPUT, Datatype.INT, 2F, 100000F, List.of())
        );
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "합이 목표값이 되는 두 원소의 인덱스를 반환하세요.",
                "정수 배열 nums와 목표값 target이 주어집니다.", "합이 target이 되는 두 원소의 인덱스를 출력합니다.",
                constraints,
                "배열을 순회하며 값을 저장하는 구조"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L))
                .thenReturn(List.of(new ProblemExample(1L, "nums=[2,7]", "[0,1]", null, 1)));
        when(runningLimitRepository.findByProblemId(1L))
                .thenReturn(List.of(new RunningLimit(1L, Language.PYTHON, 4000F, 512000)));

        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.empty());
        givenLastGenerated(1L);

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then
        assertThat(response.problemId()).isEqualTo(1L);
        assertThat(response.difficulty()).isEqualTo(Difficulty.LV1);
        // 저장하지 않은 일반 문제이고 제출한 적도 없으면 정답 카테고리는 숨김
        assertThat(response.category()).isNull();
        assertThat(response.inputFormat()).isEqualTo("정수 배열 nums와 목표값 target이 주어집니다.");
        assertThat(response.executionLimits()).hasSize(1);
        assertThat(response.executionLimits().get(0).memoryLimitKb()).isEqualTo(512000);
        assertThat(response.examples()).hasSize(1);
        assertThat(response.examples().get(0).input()).isEqualTo("nums=[2,7]");
        // 힌트 사용 기록이 없으면 0
        assertThat(response.usedHintStage()).isEqualTo(0);
        // 저장하지 않은 문제면 false
        assertThat(response.saved()).isFalse();
        // 제출한 적이 없으면 null
        assertThat(response.myApproach()).isNull();
        assertThat(response.myCode()).isNull();
    }

    @Test
    void getProblemDetail_returnsSavedHintStage_whenUsedHintExists() {
        // Given
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(new UsedHint(7L, 1L, 2)));
        givenLastGenerated(1L);

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then
        assertThat(response.usedHintStage()).isEqualTo(2);
    }

    @Test
    void getProblemDetail_returnsSavedTrue_whenUserSavedProblem() {
        // Given
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.empty());
        when(savedProblemRepository.findByUserIdAndProblemId(7L, 1L))
                .thenReturn(Optional.of(new SavedProblem(7L, 1L, false)));

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then
        assertThat(response.saved()).isTrue();
    }

    @Test
    void getProblemDetail_returnsMySubmissions_whenSubmitted() {
        // Given
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.empty());
        givenLastGenerated(1L);
        ApproachSubmission approach = new ApproachSubmission(7L, 1L, Category.ARRAY, "투 포인터로 푼다", true, 90, "좋아요");
        approach.updateKeywordResults(List.of(new KeywordResult("투 포인터", true), new KeywordResult("정렬", false)));
        when(approachSubmissionRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(approach));
        when(codeSubmissionRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(
                new CodeSubmission(7L, 1L, Language.PYTHON, "print(1)", LocalDateTime.now(), JudgingResult.WRONG_ANSWER, 1, 3)
        ));

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then
        // 카테고리를 맞춰서 제출했으면 카테고리를 내려줌
        assertThat(response.category()).isEqualTo(Category.ARRAY);
        assertThat(response.myApproach().naturalSolution()).isEqualTo("투 포인터로 푼다");
        assertThat(response.myApproach().selectedCategory()).isEqualTo(Category.ARRAY);
        assertThat(response.myCode().sourceCode()).isEqualTo("print(1)");
        assertThat(response.myCode().language()).isEqualTo(Language.PYTHON);
        assertThat(response.myApproach().result().approachScore()).isEqualTo(90);
        assertThat(response.myApproach().result().aiFeedback()).isEqualTo("좋아요");
        assertThat(response.myApproach().result().keywords()).extracting(ApproachSubmitResponse.KeywordJudgement::keyword)
                .containsExactly("투 포인터", "정렬");
        assertThat(response.myApproach().submissionUsage().usedCount()).isEqualTo(1);
        assertThat(response.myApproach().submissionUsage().remainingCount()).isEqualTo(4);
        assertThat(response.myCode().judgingResult()).isEqualTo(JudgingResult.WRONG_ANSWER);
        assertThat(response.myCode().passedTestCount()).isEqualTo(1);
        assertThat(response.myCode().totalTestCount()).isEqualTo(3);
        assertThat(response.myCode().submittedCount()).isEqualTo(1);
    }

    @Test
    void getProblemDetail_returnsEmptyKeywords_whenKeywordsWereNotSaved() {
        // Given: 키워드 판정을 저장하지 않은 이전 제출
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(usedHintRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.empty());
        givenLastGenerated(1L);
        when(approachSubmissionRepository.findByUserIdAndProblemId(7L, 1L)).thenReturn(Optional.of(
                new ApproachSubmission(7L, 1L, Category.ARRAY, "이전 풀이", false, 40, "아쉬워요")
        ));

        // When
        ProblemDetailResponse response = service().getProblemDetail(7L, 1L);

        // Then: 에러 없이 빈 키워드로 내려감
        assertThat(response.myApproach().result().keywords()).isEmpty();
    }

    @Test
    void getProblemDetail_throwsNotFound_whenProblemMissing() {
        // Given
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().getProblemDetail(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
    }

    @Test
    void getProblemDetail_opens_whenLastGeneratedToday() {
        // Given: 저장도 오늘의 문제도 아니지만 오늘 마지막으로 생성한 문제
        stubProblemForCategoryRule();
        givenLastGenerated(1L);

        // When & Then: 열리고, 카테고리는 숨김
        assertThat(service().getProblemDetail(7L, 1L).category()).isNull();
    }

    @Test
    void getProblemDetail_opens_whenUserSubmittedApproachBefore() {
        // Given: 저장도 오늘의 문제도 마지막 생성도 아니지만 내가 풀이를 제출한 적이 있는 문제 (알림에서 나중에 열 때)
        stubProblemForCategoryRule();
        when(approachSubmissionRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When & Then: 열리고, 카테고리는 숨김
        assertThat(service().getProblemDetail(7L, 1L).category()).isNull();
    }

    @Test
    void getProblemDetail_opens_whenOnlyEvaluationTaskExists() {
        // Given: 평가가 실패해서 제출 행은 없고 평가 작업만 있는 문제
        stubProblemForCategoryRule();
        when(solutionEvaluationTaskRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When & Then
        assertThat(service().getProblemDetail(7L, 1L).category()).isNull();
    }

    @Test
    void canOpen_returnsTrue_whenProblemIsTodayDaily() {
        // Given
        when(dailyProblemRepository.existsByProblemIdAndRecommendDate(1L, TODAY)).thenReturn(true);

        // When & Then
        assertThat(service().canOpen(7L, 1L)).isTrue();
    }

    @Test
    void canOpen_returnsTrue_whenUserSubmittedApproachBefore() {
        // Given
        when(approachSubmissionRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When & Then
        assertThat(service().canOpen(7L, 1L)).isTrue();
    }

    @Test
    void canOpen_returnsTrue_whenOnlyEvaluationTaskExists() {
        // Given
        when(solutionEvaluationTaskRepository.existsByUserIdAndProblemId(7L, 1L)).thenReturn(true);

        // When & Then
        assertThat(service().canOpen(7L, 1L)).isTrue();
    }

    @Test
    void canOpen_returnsTrue_whenLastGeneratedToday() {
        // Given
        givenLastGenerated(1L);

        // When & Then
        assertThat(service().canOpen(7L, 1L)).isTrue();
    }

    @Test
    void canOpen_returnsFalse_whenNoReasonToOpen() {
        // Given: 저장도, 오늘의 문제도, 제출 이력도, 마지막 생성도 아닌 문제 (다른 문제를 마지막으로 생성)
        givenLastGenerated(2L);

        // When & Then
        assertThat(service().canOpen(7L, 1L)).isFalse();
    }

    @Test
    void getProblemDetail_throwsNotFound_whenNotSavedNotDailyNotLastGenerated() {
        // Given: 문제는 있지만 저장도, 오늘의 문제도, 마지막으로 생성한 문제도 아님
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        // When & Then
        assertThatThrownBy(() -> service().getProblemDetail(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
    }

    @Test
    void getProblemDetail_throwsNotFound_whenAnotherProblemWasGeneratedLast() {
        // Given: 이 문제를 생성했더라도 그 뒤에 다른 문제(2번)를 생성해서 마지막 문제가 아님
        Problem problem = new Problem(
                Difficulty.LV1, Category.ARRAY, "두 수의 합", "내용", "입력 형식", "출력 형식", List.of(), "배열"
        );
        setId(problem, 1L);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        givenLastGenerated(2L);

        // When & Then
        assertThatThrownBy(() -> service().getProblemDetail(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
    }

    // 리플렉션으로 id 값 주입
    private void setId(Problem problem, Long id) {
        try {
            var field = Problem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(problem, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
