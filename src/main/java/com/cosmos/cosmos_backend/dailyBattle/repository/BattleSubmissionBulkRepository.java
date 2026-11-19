package com.cosmos.cosmos_backend.dailyBattle.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BattleSubmissionBulkRepository {

    private static final String INSERT_PREFIX = """
            INSERT INTO battle_submissions
                (participation_id, battle_case_id, submitted_output, is_correct)
            VALUES
            """;

    private final JdbcTemplate jdbcTemplate;

    // 여러 답안을 하나의 다중 행 INSERT 문으로 저장
    public void insertAll(Long participationId, List<SubmissionRow> submissionRows) {
        if (submissionRows.isEmpty()) {
            return;
        }

        String valuesClause = String.join(", ",
                submissionRows.stream()
                        .map(submissionRow -> "(?, ?, ?, NULL)")
                        .toList()
        );

        List<Object> parameters = new ArrayList<>(submissionRows.size() * 3);

        for (SubmissionRow submissionRow : submissionRows) {
            parameters.add(participationId);
            parameters.add(submissionRow.battleCaseId());
            parameters.add(submissionRow.submittedOutput());
        }

        jdbcTemplate.update(INSERT_PREFIX + valuesClause, parameters.toArray());
    }

    public record SubmissionRow(
            Long battleCaseId,
            String submittedOutput
    ) {
    }
}
