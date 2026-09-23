package com.cosmos.cosmos_backend.ActivityRecord.dto;

import java.time.LocalDate;
import java.util.List;

public record ActivityRecordResponseDto(
        LocalDate startDate,
        LocalDate endDate,
        List<Activities> activities
) {

    public record Activities(
            LocalDate activityDate,
            Long correctProblemCount
    ){

    }
}
