package com.son.soccerStreaming.dto;

import lombok.*;


public class PlayerResponseDto {

    @Getter
    @Builder
    public static class Summary {
        Long playerId;
        String playerName;
        int backNumber;
    }

    @Getter
    @Builder
    public static class Details {
        Long playerId;
        String playerName;
        int backNumber;
        int age;
        int height;
        int weight;
        String position;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SeasonStats {
        Long playerId;
        int totalMatches;
        int goals;
        int assists;
        int shots;
        int shotsOnTarget;
        int totalPasses;
        double passAccuracy;
        int fouls;
        int tackles;
    }
}
