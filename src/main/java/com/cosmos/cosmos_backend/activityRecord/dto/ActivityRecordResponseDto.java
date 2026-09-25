package com.cosmos.cosmos_backend.activityRecord.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record ActivityRecordResponseDto(

        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonProperty("end_date")
        LocalDate endDate,

        List<Activities> activities
) {

    public record Activities(

            @JsonProperty("activity_date")
            LocalDate activityDate,

            @JsonProperty("correct_problem_count")
            Long correctProblemCount
    ){

    }
}
