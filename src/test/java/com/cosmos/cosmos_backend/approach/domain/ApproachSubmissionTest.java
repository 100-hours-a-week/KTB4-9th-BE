package com.cosmos.cosmos_backend.approach.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cosmos.cosmos_backend.common.Category;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ApproachSubmissionTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 19, 3, 30, 0);

    @Test
    void constructor_withoutSubmittedAt_usesCurrentTime() {
        // When
        ApproachSubmission submission = new ApproachSubmission(1L, 10L, Category.ARRAY, "풀이", true, 90, "좋아요");

        // Then
        assertThat(submission.getSubmittedAt()).isNotNull();
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
    }

    @Test
    void constructor_withSubmittedAt_keepsGivenTime() {
        // When
        ApproachSubmission submission = new ApproachSubmission(1L, 10L, Category.ARRAY, "풀이", true, 90, "좋아요", REQUESTED_AT);

        // Then
        assertThat(submission.getSubmittedAt()).isEqualTo(REQUESTED_AT);
        assertThat(submission.getSubmittedCount()).isEqualTo(1);
        assertThat(submission.getEvaluatedAt()).isNotNull();
    }

    @Test
    void resubmit_withSubmittedAt_keepsGivenTime_andIncreasesCount() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(1L, 10L, Category.ARRAY, "이전 풀이", false, 20, "아쉬워요");

        // When
        submission.resubmit(Category.DP, "새 풀이", true, 95, "좋아요", REQUESTED_AT);

        // Then
        assertThat(submission.getSubmittedAt()).isEqualTo(REQUESTED_AT);
        assertThat(submission.getSubmittedCount()).isEqualTo(2);
        assertThat(submission.getNaturalSolution()).isEqualTo("새 풀이");
        assertThat(submission.getTotalScore()).isEqualTo(95);
    }

    @Test
    void resubmit_withoutSubmittedAt_stillIncreasesCount() {
        // Given
        ApproachSubmission submission = new ApproachSubmission(1L, 10L, Category.ARRAY, "이전 풀이", false, 20, "아쉬워요", REQUESTED_AT);

        // When
        submission.resubmit(Category.DP, "새 풀이", true, 95, "좋아요");

        // Then
        assertThat(submission.getSubmittedCount()).isEqualTo(2);
        assertThat(submission.getSubmittedAt()).isAfter(REQUESTED_AT);
    }
}
