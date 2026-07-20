package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ApiFootballFixtureStatisticsDto {

    private ApiFootballFixtureStatisticsDto() {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse<T> extends ApiFootballResponseEnvelope<T> {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureStatisticsResponse {
        private ApiFootballLiveDto.TeamInfo team;
        private List<Statistic> statistics;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statistic {
        private String type;
        private Object value;
    }
}
