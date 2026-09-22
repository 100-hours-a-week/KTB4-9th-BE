package com.cosmos.cosmos_backend.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.*;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.home.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.Problem;
import com.cosmos.cosmos_backend.problem.domain.ProblemExample;
import com.cosmos.cosmos_backend.problem.domain.RunningLimit;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemConstraints;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.repository.ProblemExampleRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.RunningLimitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ProblemExampleRepository problemExampleRepository;

    @Mock
    private RunningLimitRepository runningLimitRepository;

    private ProblemService service() {
        return new ProblemService(problemRepository, problemExampleRepository, runningLimitRepository);
    }

    @Test
    void getProblemDetail_returnsAssembledResponse_whenProblemExists() {
        // Given
        ProblemConstraints constraints = new ProblemConstraints(
                List.of(new AiProblemsCreateRequestDto.InputConstraints("nums.length", Scope.INPUT, Datatype.INT, 2F, 100000F, List.of()))
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
                .thenReturn(List.of(new RunningLimit(1L, Language.PYTHON, 4000F, 500)));

        // When
        ProblemDetailResponse response = service().getProblemDetail(1L);

        // Then
        assertThat(response.problemId()).isEqualTo(1L);
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.category()).isEqualTo("ARRAY");
        assertThat(response.inputFormat()).isEqualTo("정수 배열 nums와 목표값 target이 주어집니다.");
        assertThat(response.executionLimits()).hasSize(1);
        assertThat(response.executionLimits().get(0).memoryLimitKb()).isEqualTo(500 * 1024);
        assertThat(response.examples()).hasSize(1);
        assertThat(response.examples().get(0).input()).isEqualTo("nums=[2,7]");
    }

    @Test
    void getProblemDetail_throwsNotFound_whenProblemMissing() {
        // Given
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().getProblemDetail(999L))
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
