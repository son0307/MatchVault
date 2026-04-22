package com.son.soccerStreaming.dto;

import com.son.soccerStreaming.entity.MatchStatus;
import lombok.Builder;
import lombok.Getter;

public class MatchResponseDto {
    @Getter
    @Builder
    public static class Summary {
        private String matchId;
        private String homeTeamName;
        private String awayTeamName;
        private int homeScore;
        private int awayScore;
        private MatchStatus matchStatus;
    }
}
