package com.cosmos.cosmos_backend.problem.controller;

import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.common.exception.GlobalExceptionHandler;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemDetailResponse;
import com.cosmos.cosmos_backend.problem.service.ProblemService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProblemControllerTest {

    @Mock
    private ProblemService problemService;

    private MockMvcTester mvc() {
        ProblemController controller = new ProblemController(problemService);
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build());
    }

    @Test
    void getProblemDetail_returns200WithBody_whenProblemExists() {
        // Given
        ProblemDetailResponse response = new ProblemDetailResponse(
                1L, 1, "ARRAY", "두 수의 합", "내용",
                "입력 형식", "출력 형식", List.of(), List.of(), List.of()
        );
        when(problemService.getProblemDetail(1L)).thenReturn(response);

        // When & Then
        mvc().get().uri("/problems/1")
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_detail_retrieval_success");
    }

    @Test
    void getProblemDetail_returns404_whenServiceThrowsNotFound() {
        // Given
        when(problemService.getProblemDetail(999L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found"));

        // When & Then
        mvc().get().uri("/problems/999")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("problem_not_found");
    }

    @Test
    void getProblemDetail_returns400_whenProblemIdNotNumeric() {
        mvc().get().uri("/problems/abc")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("invalid_problem_id");
    }
}
