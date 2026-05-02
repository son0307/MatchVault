package com.son.soccerStreaming.dto;

import com.son.soccerStreaming.entity.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class MatchResponseDto {
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private Long matchId;
        private String homeTeamName;
        private String awayTeamName;
        private int homeScore;
        private int awayScore;
        private MatchStatus matchStatus;
    }
}
