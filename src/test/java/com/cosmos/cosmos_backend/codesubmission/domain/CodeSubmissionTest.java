package com.cosmos.cosmos_backend.codesubmission.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.common.Language;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CodeSubmissionTest {

    private static final LocalDateTime RECEIVED_AT = LocalDateTime.now().minusSeconds(2);

    @Test
    void firstSubmission_isCompletedWithCountOne() {
        // When
        CodeSubmission submission = new CodeSubmission(
                7L, 1L, Language.PYTHON, "print(1)", RECEIVED_AT, JudgingResult.WRONG_ANSWER, 3, 10
        );

        // Then
        assertThat(submission.getUserId()).isEqualTo(7L);
        assertThat(submission.getProblemId()).isEqualTo(1L);
        assertThat(submission.getLanguage()).isEqualTo(Language.PYTHON);
        assertThat(submission.getSourceCode()).isEqualTo("print(1)");
        assertThat(submission.getJudgingStatus()).isEqualTo(JudgingStatus.COMPLETED);
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.WRONG_ANSWER);
        assertThat(submission.getPassedTestCount()).isEqualTo(3);
        assertThat(submission.getTotalTestCount()).isEqualTo(10);
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
    }

    @Test
    void firstSubmission_keepsReceivedTimeAndSetsJudgedTimeAfterIt() {
        // When
        CodeSubmission submission = new CodeSubmission(
                7L, 1L, Language.JAVA, "class Main {}", RECEIVED_AT, JudgingResult.CORRECT, 10, 10
        );

        // Then
        assertThat(submission.getSubmittedAt()).isEqualTo(RECEIVED_AT);
        assertThat(submission.getJudgedAt()).isNotNull();
        assertThat(submission.getJudgedAt()).isAfter(submission.getSubmittedAt());
    }

    @Test
    void resubmit_increasesCountAndOverwritesPreviousResult() {
        // Given
        CodeSubmission submission = new CodeSubmission(
                7L, 1L, Language.PYTHON, "print(1)", RECEIVED_AT, JudgingResult.WRONG_ANSWER, 3, 10
        );
        LocalDateTime secondReceivedAt = LocalDateTime.now().minusSeconds(1);

        // When
        submission.resubmit(Language.JAVASCRIPT, "console.log(2)", secondReceivedAt, JudgingResult.CORRECT, 10, 10);

        // Then
        assertThat(submission.getSubmittedCount()).isEqualTo(2);
        assertThat(submission.getLanguage()).isEqualTo(Language.JAVASCRIPT);
        assertThat(submission.getSourceCode()).isEqualTo("console.log(2)");
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.CORRECT);
        assertThat(submission.getPassedTestCount()).isEqualTo(10);
        assertThat(submission.getTotalTestCount()).isEqualTo(10);
        assertThat(submission.getSubmittedAt()).isEqualTo(secondReceivedAt);
        assertThat(submission.getJudgingStatus()).isEqualTo(JudgingStatus.COMPLETED);
    }

    @Test
    void resubmit_keepsUserAndProblem() {
        // Given
        CodeSubmission submission = new CodeSubmission(
                7L, 1L, Language.CPP, "int main() {}", RECEIVED_AT, JudgingResult.COMPILE_ERROR, 0, 5
        );

        // When
        submission.resubmit(Language.CPP, "int main() { return 0; }", LocalDateTime.now(), JudgingResult.CORRECT, 5, 5);

        // Then
        assertThat(submission.getUserId()).isEqualTo(7L);
        assertThat(submission.getProblemId()).isEqualTo(1L);
    }

    @Test
    void resubmit_countGrowsUpToMaxSubmissionCount() {
        // Given
        CodeSubmission submission = new CodeSubmission(
                7L, 1L, Language.PYTHON, "a", RECEIVED_AT, JudgingResult.RUNTIME_ERROR, 0, 4
        );

        // When
        for (int i = 1; i < CodeSubmission.MAX_SUBMISSION_COUNT; i++) {
            submission.resubmit(Language.PYTHON, "a" + i, LocalDateTime.now(), JudgingResult.TIME_LIMIT_EXCEEDED, 1, 4);
        }

        // Then
        assertThat(submission.getSubmittedCount()).isEqualTo(CodeSubmission.MAX_SUBMISSION_COUNT);
        assertThat(submission.getJudgingResult()).isEqualTo(JudgingResult.TIME_LIMIT_EXCEEDED);
    }
}
