package com.son.soccerStreaming.dto;

import lombok.*;


public class PlayerResponseDto {

    @Getter
    @Builder
    public static class Summary {
        String playerId;
        String playerName;
        int backNumber;
    }

    @Getter
    @Builder
    public static class Details {
        String playerId;
        String playerName;
        int backNumber;
        int age;
        int height;
        int weight;
        String mainPosition;
        String subPosition;
    }

    @Getter
    @Builder
    public static class SeasonStats {
        String playerId;
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
