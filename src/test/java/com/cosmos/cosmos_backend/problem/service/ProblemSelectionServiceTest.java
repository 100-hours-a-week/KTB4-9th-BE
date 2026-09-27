package com.cosmos.cosmos_backend.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.DailyGeneratedCount;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.domain.entity.ProblemExample;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemSelectionResponse;
import com.cosmos.cosmos_backend.problem.repository.DailyGeneratedCountRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemExampleRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ProblemSelectionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemService problemService;

    @Mock
    private ProblemExampleRepository problemExampleRepository;

    @Mock
    private DailyGeneratedCountRepository dailyGeneratedCountRepository;

    private ProblemSelectionService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(Instant.parse("2026-09-05T01:00:00Z")); // KST 2026-09-05 10:00
    }

    private ProblemSelectionService serviceAt(Instant now) {
        // TransactionTemplate 없이도 콜백이 바로 실행되도록 최소한으로 흉내냄 (진짜 트랜잭션은 안 씀)
        TransactionTemplate fakeTransactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        return new ProblemSelectionService(
                problemRepository, problemService, problemExampleRepository, dailyGeneratedCountRepository,
                fakeTransactionTemplate, Clock.fixed(now, KST)
        );
    }

    private Problem problem() {
        Problem problem = new Problem(
                Difficulty.LV3, Category.DP, "계단 오르기", "내용", "입력 형식", "출력 형식",
                List.<AiProblemsCreateRequestDto.InputConstraints>of(), "선정 배경"
        );
        try {
            var field = Problem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(problem, 7L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return problem;
    }

    // 오늘 사용 횟수가 count인 행을 만든다.
    private DailyGeneratedCount countRow(LocalDate date, int count) {
        DailyGeneratedCount row = new DailyGeneratedCount(10L, date);
        for (int i = 1; i < count; i++) {
            row.increase();
        }
        return row;
    }

    @Test
    void select_returnsProblemAndUsageOne_whenFirstUseToday() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L))
                .thenReturn(List.of(new ProblemExample(7L, "3", "3", "세 가지", 1)));

        // When
        ProblemSelectionResponse response = service.select(10L, "3", "RANDOM");

        // Then
        assertThat(response.problem().problemId()).isEqualTo(7L);
        assertThat(response.problem().level()).isEqualTo(3);
        assertThat(response.problem().category()).isEqualTo(Category.DP);
        assertThat(response.problem().title()).isEqualTo("계단 오르기");
        assertThat(response.problem().examples()).hasSize(1);
        assertThat(response.problem().examples().get(0).input()).isEqualTo("3");
        assertThat(response.dailyUsage().date()).isEqualTo(TODAY);
        assertThat(response.dailyUsage().limit()).isEqualTo(3);
        assertThat(response.dailyUsage().usedCount()).isEqualTo(1);
        assertThat(response.dailyUsage().remainingCount()).isEqualTo(2);
    }

    @Test
    void select_increasesUsageToThree_whenAlreadyUsedTwice() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.of(2));
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY))
                .thenReturn(Optional.of(countRow(TODAY, 2)));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        ProblemSelectionResponse response = service.select(10L, "3", null);

        // Then
        assertThat(response.dailyUsage().usedCount()).isEqualTo(3);
        assertThat(response.dailyUsage().remainingCount()).isEqualTo(0);
    }

    @Test
    void select_acceptsBothNumberAndLvPrefixForLevel() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        service.select(10L, "3", null);
        service.select(10L, "LV3", null);

        // Then
        verify(problemRepository, times(2)).findRandomUnsolved("LV3", null, 10L);
    }

    @Test
    void select_throwsBadRequest_whenLevelMissing() {
        // When & Then
        assertThatThrownBy(() -> service.select(10L, null, "DP"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("problem_level_is_required");
        assertThatThrownBy(() -> service.select(10L, " ", "DP"))
                .hasMessage("problem_level_is_required");

        verifyNoInteractions(problemRepository, dailyGeneratedCountRepository);
    }

    @Test
    void select_throwsBadRequest_whenLevelInvalid() {
        // When & Then
        assertThatThrownBy(() -> service.select(10L, "9", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("invalid_problem_level");
        assertThatThrownBy(() -> service.select(10L, "abc", null))
                .hasMessage("invalid_problem_level");

        verifyNoInteractions(problemRepository, dailyGeneratedCountRepository);
    }

    @Test
    void select_throwsBadRequest_whenCategoryInvalid() {
        // When & Then
        assertThatThrownBy(() -> service.select(10L, "3", "NOPE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("invalid_problem_category");

        verifyNoInteractions(problemRepository, dailyGeneratedCountRepository);
    }

    @Test
    void select_passesCategoryToQuery_whenCategorySpecified() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", "DP", 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        service.select(10L, "3", "DP");

        // Then
        verify(problemRepository).findRandomUnsolved("LV3", "DP", 10L);
    }

    @Test
    void select_throws429WithResetAt_andSkipsProblemQuery_whenLimitAlreadyReached() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.of(3));

        // When & Then
        assertThatThrownBy(() -> service.select(10L, "3", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    var data = (ProblemSelectionResponse.DailyLimitExceededData) be.getData();
                    assertThat(data.date()).isEqualTo(TODAY);
                    assertThat(data.limit()).isEqualTo(3);
                    assertThat(data.usedCount()).isEqualTo(3);
                    assertThat(data.remainingCount()).isEqualTo(0);
                    assertThat(data.resetAt()).isEqualTo(OffsetDateTime.of(2026, 9, 6, 0, 0, 0, 0, ZoneOffset.ofHours(9)));
                })
                .hasMessage("daily_problem_limit_exceeded");

        verifyNoInteractions(problemRepository, problemService);
    }

    @Test
    void select_generatesProblemWithAi_andIncreasesCount_whenNoUnsolvedProblem() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.of(1));
        when(problemRepository.findRandomUnsolved("LV3", "DP", 10L)).thenReturn(Optional.empty());
        when(problemService.createOnDemandProblem(Difficulty.LV3, Category.DP)).thenReturn(problem());
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.of(countRow(TODAY, 1)));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        ProblemSelectionResponse response = service.select(10L, "3", "DP");

        // Then
        assertThat(response.problem().problemId()).isEqualTo(7L);
        assertThat(response.dailyUsage().usedCount()).isEqualTo(2);
    }

    @Test
    void select_doesNotCallAi_whenUnsolvedProblemExists() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        service.select(10L, "3", null);

        // Then
        verifyNoInteractions(problemService);
    }

    @Test
    void select_asksAiWithConcreteCategory_whenCategoryIsRandom() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.empty());
        when(problemService.createOnDemandProblem(eq(Difficulty.LV3), any(Category.class))).thenReturn(problem());
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        service.select(10L, "3", "RANDOM");

        // Then
        var captor = org.mockito.ArgumentCaptor.forClass(Category.class);
        verify(problemService).createOnDemandProblem(eq(Difficulty.LV3), captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void select_throws503_andDoesNotIncreaseCount_whenAiServerUnreachable() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.of(1));
        when(problemRepository.findRandomUnsolved("LV3", "DP", 10L)).thenReturn(Optional.empty());
        when(problemService.createOnDemandProblem(Difficulty.LV3, Category.DP))
                .thenThrow(new ResourceAccessException("connection refused"));

        // When & Then
        assertThatThrownBy(() -> service.select(10L, "3", "DP"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessage("problem_generation_unavailable");

        verify(dailyGeneratedCountRepository, never()).findForUpdateByUserIdAndUsageDate(any(), any());
        verify(dailyGeneratedCountRepository, never()).save(any());
    }

    @Test
    void select_passesThroughBusinessException_andDoesNotIncreaseCount_whenAiResponseInvalid() {
        // Given
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", "DP", 10L)).thenReturn(Optional.empty());
        when(problemService.createOnDemandProblem(Difficulty.LV3, Category.DP))
                .thenThrow(new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ai_problem_creation_failed"));

        // When & Then
        assertThatThrownBy(() -> service.select(10L, "3", "DP"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .hasMessage("ai_problem_creation_failed");

        verify(dailyGeneratedCountRepository, never()).save(any());
    }

    @Test
    void select_throws429_whenLimitReachedByAnotherRequestAfterLock() {
        // Given (빠른 확인 시점엔 2회였는데, 잠근 뒤엔 다른 요청이 올려서 3회가 된 상황)
        when(dailyGeneratedCountRepository.findCount(10L, TODAY)).thenReturn(Optional.of(2));
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, TODAY))
                .thenReturn(Optional.of(countRow(TODAY, 3)));

        // When & Then
        assertThatThrownBy(() -> service.select(10L, "3", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessage("daily_problem_limit_exceeded");

        verify(problemExampleRepository, never()).findByProblemIdOrderByDisplayOrder(any());
    }

    @Test
    void select_startsFromOne_whenDateChanged() {
        // Given (어제까지 3회를 썼어도 날짜가 바뀌면 오늘 기록은 없음)
        ProblemSelectionService nextDayService = serviceAt(Instant.parse("2026-09-05T15:30:00Z")); // KST 2026-09-06 00:30
        LocalDate nextDay = LocalDate.of(2026, 9, 6);
        when(dailyGeneratedCountRepository.findCount(10L, nextDay)).thenReturn(Optional.empty());
        when(problemRepository.findRandomUnsolved("LV3", null, 10L)).thenReturn(Optional.of(problem()));
        when(dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(10L, nextDay)).thenReturn(Optional.empty());
        when(dailyGeneratedCountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemExampleRepository.findByProblemIdOrderByDisplayOrder(7L)).thenReturn(List.of());

        // When
        ProblemSelectionResponse response = nextDayService.select(10L, "3", null);

        // Then
        assertThat(response.dailyUsage().date()).isEqualTo(nextDay);
        assertThat(response.dailyUsage().usedCount()).isEqualTo(1);
    }
}
