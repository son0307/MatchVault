package com.son.soccerStreaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class PlayerResponseDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        String playerId;
        String name;
        int backNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Details {
        String playerId;
        String name;
        int backNumber;
        int age;
        int height;
        int weight;
        String mainPosition;
        String subPosition;
    }
}
