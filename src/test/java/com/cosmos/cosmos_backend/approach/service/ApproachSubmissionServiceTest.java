package com.cosmos.cosmos_backend.approach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.approach.client.AiEvaluationClient;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationRequest;
import com.cosmos.cosmos_backend.approach.client.AiEvaluationResult;
import com.cosmos.cosmos_backend.approach.domain.ApproachSubmission;
import com.cosmos.cosmos_backend.approach.dto.ApproachSubmitResult;
import com.cosmos.cosmos_backend.approach.repository.ApproachSubmissionRepository;
import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.Keyword;
import com.cosmos.cosmos_backend.problem.domain.Problem;
import com.cosmos.cosmos_backend.problem.domain.RunningLimit;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemConstraints;
import com.cosmos.cosmos_backend.problem.repository.KeywordRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ApproachSubmissionServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private RunningLimitRepository runningLimitRepository;

    @Mock
    private ApproachSubmissionRepository approachSubmissionRepository;

    @Mock
    private AiEvaluationClient aiEvaluationClient;

    private ApproachSubmissionService service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate 없이도 콜백이 바로 실행되도록 최소한으로 흉내냄 (진짜 트랜잭션은 안 씀)
        TransactionTemplate fakeTransactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        service = new ApproachSubmissionService(
                problemRepository, keywordRepository, runningLimitRepository, approachSubmissionRepository,
                aiEvaluationClient, fakeTransactionTemplate
        );
    }

    private Problem problem(String category) {
        return new Problem("LV1", category, "제목", "내용", "입력 형식", "출력 형식", new ProblemConstraints(List.of()), "선정 배경");
    }

    private AiEvaluationResult aiSuccess(String... includedKeywords) {
        List<AiEvaluationResult.KeywordJudgement> judgements = List.of(includedKeywords).stream()
                .map(k -> new AiEvaluationResult.KeywordJudgement(k, true))
                .toList();
        return new AiEvaluationResult(85, "좋은 접근이에요", judgements);
    }

    @Test
    void submit_createsNewSubmission_whenFirstTimeAndAiSucceeds() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any())).thenReturn(aiSuccess());
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ApproachSubmitResult result = service.submit(10L, 1L, "ARRAY", "이렇게 풉니다");

        // Then
        ApproachSubmission submission = result.submission();
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
        assertThat(submission.getCategoryResult()).isTrue();
        assertThat(submission.getEvaluationStatus().name()).isEqualTo("COMPLETED");
        assertThat(submission.getTotalScore()).isEqualTo(85);
        assertThat(submission.getAiFeedback()).isEqualTo("좋은 접근이에요");
    }

    @Test
    void submit_incrementsCountAndOverwritesResult_whenResubmitting() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("GRAPH")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any())).thenReturn(aiSuccess());
        ApproachSubmission existing = new ApproachSubmission(10L, 1L, Category.ARRAY, "이전 풀이", false, 20, "이전 피드백");
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.of(existing));

        // When
        ApproachSubmitResult result = service.submit(10L, 1L, "GRAPH", "새 풀이");

        // Then
        ApproachSubmission submission = result.submission();
        assertThat(submission.getSubmittedCount()).isEqualTo(2);
        assertThat(submission.getCategoryResult()).isTrue();
        assertThat(submission.getTotalScore()).isEqualTo(85);
        assertThat(submission.getNaturalSolution()).isEqualTo("새 풀이");
    }

    @Test
    void submit_setsCategoryResultFalse_butStillCallsAi_whenSelectedCategoryWrong() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("DP")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any())).thenReturn(aiSuccess());
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ApproachSubmitResult result = service.submit(10L, 1L, "GREEDY", "이렇게 풉니다");

        // Then
        assertThat(result.submission().getCategoryResult()).isFalse();
        verify(aiEvaluationClient).evaluate(any());
    }

    @Test
    void submit_savesNothing_whenAiFails() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any()))
                .thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ai_server_unavailable"));

        // When & Then
        assertThatThrownBy(() -> service.submit(10L, 1L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(approachSubmissionRepository, never()).save(any());
        verify(approachSubmissionRepository, never()).findByUserIdAndProblemId(any(), any());
    }

    @Test
    void submit_mergesKeywordJudgements_missingIsFalse_unknownIsDropped() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of(
                new Keyword(1L, "정렬"), new Keyword(1L, "이분 탐색"), new Keyword(1L, "투 포인터")
        ));
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any())).thenReturn(new AiEvaluationResult(
                50, "피드백",
                List.of(
                        new AiEvaluationResult.KeywordJudgement("정렬", true),
                        new AiEvaluationResult.KeywordJudgement("AI가 준 모르는 키워드", true)
                )
        ));
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ApproachSubmitResult result = service.submit(10L, 1L, "ARRAY", "풀이");

        // Then
        assertThat(result.keywords()).containsExactly(
                new AiEvaluationResult.KeywordJudgement("정렬", true),
                new AiEvaluationResult.KeywordJudgement("이분 탐색", false),
                new AiEvaluationResult.KeywordJudgement("투 포인터", false)
        );
    }

    @Test
    void submit_sendsProblemAnswerCategory_notSelectedCategory_toAi() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("DP")));
        when(keywordRepository.findByProblemIdOrderById(1L)).thenReturn(List.of());
        when(runningLimitRepository.findByProblemId(1L)).thenReturn(List.of());
        when(aiEvaluationClient.evaluate(any())).thenReturn(aiSuccess());
        when(approachSubmissionRepository.findByUserIdAndProblemId(10L, 1L)).thenReturn(Optional.empty());
        when(approachSubmissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.submit(10L, 1L, "GREEDY", "풀이");

        // Then
        var captor = org.mockito.ArgumentCaptor.forClass(AiEvaluationRequest.class);
        verify(aiEvaluationClient).evaluate(captor.capture());
        assertThat(captor.getValue().category()).isEqualTo("DP");
        assertThat(captor.getValue().naturalSolution()).isEqualTo("풀이");
    }

    @Test
    void submit_throwsNotFound_whenProblemMissing_andNeverCallsAi() {
        // Given
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.submit(10L, 999L, "ARRAY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(aiEvaluationClient);
    }

    @Test
    void submit_throwsBadRequest_whenSelectedCategoryInvalid_andNeverCallsAi() {
        // Given
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem("ARRAY")));

        // When & Then
        assertThatThrownBy(() -> service.submit(10L, 1L, "NOT_A_CATEGORY", "풀이"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("invalid_selected_category");

        verifyNoInteractions(aiEvaluationClient);
    }
}
