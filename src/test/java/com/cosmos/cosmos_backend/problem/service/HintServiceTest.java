package com.cosmos.cosmos_backend.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.Hint;
import com.cosmos.cosmos_backend.problem.domain.entity.UsedHint;
import com.cosmos.cosmos_backend.problem.dto.response.HintResponse;
import com.cosmos.cosmos_backend.problem.repository.HintRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.UsedHintRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class HintServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PROBLEM_ID = 1L;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private HintRepository hintRepository;

    @Mock
    private UsedHintRepository usedHintRepository;

    private HintService service() {
        return new HintService(problemRepository, hintRepository, usedHintRepository);
    }

    // 문제가 있고, 해당 힌트가 있는 상태를 만든다.
    private void givenHint(Language language, HintType type, String content) {
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(hintRepository.findByProblemIdAndLanguageAndHintType(PROBLEM_ID, language, type))
                .thenReturn(Optional.of(new Hint(PROBLEM_ID, language, type, content)));
    }

    @Test
    void commentHint_savesStage1_whenFirstUse() {
        // Given
        givenHint(Language.PYTHON, HintType.COMMENT, "# 힌트");
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());

        // When
        HintResponse response = service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.COMMENT);

        // Then
        assertThat(response.problemId()).isEqualTo(PROBLEM_ID);
        assertThat(response.hintType()).isEqualTo("COMMENT");
        assertThat(response.hintStage()).isEqualTo(1);
        assertThat(response.content()).isEqualTo("# 힌트");
        ArgumentCaptor<UsedHint> saved = ArgumentCaptor.forClass(UsedHint.class);
        verify(usedHintRepository).save(saved.capture());
        assertThat(saved.getValue().getHintStage()).isEqualTo(1);
    }

    @Test
    void commentHint_keepsStage_whenAlreadyUsed() {
        // Given
        givenHint(Language.PYTHON, HintType.COMMENT, "# 힌트");
        UsedHint used = new UsedHint(USER_ID, PROBLEM_ID, 1);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(used));

        // When
        service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.COMMENT);

        // Then
        assertThat(used.getHintStage()).isEqualTo(1);
        verify(usedHintRepository, never()).save(any());
    }

    @Test
    void answerHint_raisesStageTo2_whenStage1() {
        // Given
        givenHint(Language.PYTHON, HintType.SOLUTION, "print(1)");
        UsedHint used = new UsedHint(USER_ID, PROBLEM_ID, 1);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(used));

        // When
        HintResponse response = service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.SOLUTION);

        // Then
        assertThat(used.getHintStage()).isEqualTo(2);
        assertThat(response.hintType()).isEqualTo("ANSWER");
        assertThat(response.hintStage()).isEqualTo(2);
        verify(usedHintRepository, never()).save(any());
    }

    @Test
    void answerHint_throwsConflictAndDoesNotSave_whenNoCommentHintUsedBefore() {
        // Given (409는 힌트 조회 전에 나므로 hintRepository는 stub하지 않음)
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.SOLUTION))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessage("comment_hint_required");
        verify(hintRepository, never()).findByProblemIdAndLanguageAndHintType(any(), any(), any());
        verify(usedHintRepository, never()).save(any());
    }

    @Test
    void answerHint_succeeds_whenCommentHintWasUsedInAnotherLanguage() {
        // Given (사용 단계는 언어와 무관하게 문제 단위로 관리됨)
        givenHint(Language.JAVA, HintType.SOLUTION, "class A {}");
        UsedHint used = new UsedHint(USER_ID, PROBLEM_ID, 1);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(used));

        // When
        HintResponse response = service().getHint(USER_ID, PROBLEM_ID, "JAVA", HintType.SOLUTION);

        // Then
        assertThat(response.hintStage()).isEqualTo(2);
        assertThat(used.getHintStage()).isEqualTo(2);
    }

    @Test
    void answerHint_throwsNotFound_beforeConflict_whenProblemMissing() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.SOLUTION))
                .hasMessage("problem_not_found");
        verifyNoInteractions(usedHintRepository);
    }

    @Test
    void answerHint_throwsBadRequest_beforeConflict_whenLanguageInvalid() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "RUBY", HintType.SOLUTION))
                .hasMessage("invalid_language");
        verifyNoInteractions(usedHintRepository);
    }

    @Test
    void answerHint_keepsStage2_whenReRequested() {
        // Given
        givenHint(Language.JAVA, HintType.SOLUTION, "class A {}");
        UsedHint used = new UsedHint(USER_ID, PROBLEM_ID, 2);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(used));

        // When
        service().getHint(USER_ID, PROBLEM_ID, "JAVA", HintType.SOLUTION);

        // Then
        assertThat(used.getHintStage()).isEqualTo(2);
    }

    @Test
    void commentHint_doesNotLowerStage_whenStage2() {
        // Given
        givenHint(Language.CPP, HintType.COMMENT, "// 힌트");
        UsedHint used = new UsedHint(USER_ID, PROBLEM_ID, 2);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(used));

        // When
        HintResponse response = service().getHint(USER_ID, PROBLEM_ID, "CPP", HintType.COMMENT);

        // Then
        assertThat(used.getHintStage()).isEqualTo(2);
        assertThat(response.hintStage()).isEqualTo(1);
        assertThat(response.content()).isEqualTo("// 힌트");
    }

    @Test
    void getHint_returnsHintOfRequestedLanguage_forAllLanguages() {
        for (Language language : Language.values()) {
            // Given
            HintRepository repo = org.mockito.Mockito.mock(HintRepository.class);
            when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
            when(repo.findByProblemIdAndLanguageAndHintType(PROBLEM_ID, language, HintType.COMMENT))
                    .thenReturn(Optional.of(new Hint(PROBLEM_ID, language, HintType.COMMENT, language.name() + " 힌트")));
            when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());

            // When
            HintResponse response = new HintService(problemRepository, repo, usedHintRepository)
                    .getHint(USER_ID, PROBLEM_ID, language.name(), HintType.COMMENT);

            // Then
            assertThat(response.content()).isEqualTo(language.name() + " 힌트");
        }
    }

    @Test
    void getHint_throwsNotFound_whenProblemMissing() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.COMMENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("problem_not_found");
    }

    @Test
    void getHint_throwsBadRequest_whenLanguageInvalid() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "RUBY", HintType.COMMENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessage("invalid_language");
    }

    @Test
    void commentHint_throwsNotFoundAndDoesNotSave_whenHintMissing() {
        // Given
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(hintRepository.findByProblemIdAndLanguageAndHintType(PROBLEM_ID, Language.PYTHON, HintType.COMMENT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.COMMENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("comment_hint_not_found");
        verify(usedHintRepository, never()).save(any());
    }

    @Test
    void answerHint_throwsAnswerHintNotFound_whenHintMissing() {
        // Given (주석 힌트를 본 상태여야 409가 아니라 힌트 조회까지 진행됨)
        when(problemRepository.existsById(PROBLEM_ID)).thenReturn(true);
        when(usedHintRepository.findByUserIdAndProblemId(USER_ID, PROBLEM_ID))
                .thenReturn(Optional.of(new UsedHint(USER_ID, PROBLEM_ID, 1)));
        when(hintRepository.findByProblemIdAndLanguageAndHintType(PROBLEM_ID, Language.PYTHON, HintType.SOLUTION))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service().getHint(USER_ID, PROBLEM_ID, "PYTHON", HintType.SOLUTION))
                .isInstanceOf(BusinessException.class)
                .hasMessage("answer_hint_not_found");
        verify(usedHintRepository, never()).save(any());
    }
}
