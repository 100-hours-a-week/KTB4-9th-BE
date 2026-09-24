package com.cosmos.cosmos_backend.ranking.dto;

import java.util.List;

public record GlobalRankingResponseDto(

        RankingFilterResponseDto filter,

        MyRankingResponseDto myRanking,

        List<RankingCommonResponseDto> rankings
) {
}
