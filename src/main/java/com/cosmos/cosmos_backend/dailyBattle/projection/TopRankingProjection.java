package com.cosmos.cosmos_backend.dailyBattle.projection;

public interface TopRankingProjection {
    Long getRank();

    Long getUserId();

    String getUserName();

    String getProfileImageUrl();

    Long getElapsedMicros();
}