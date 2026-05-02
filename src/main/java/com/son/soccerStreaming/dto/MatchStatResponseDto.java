package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchStatResponseDto {
    private Long matchId;
    private TeamStatSummary homeTeamStat;
    private TeamStatSummary awayTeamStat;

    @Getter
    @Builder
    public static class TeamStatSummary {
        private Long teamId;       // 💡 String -> Long
        private int score;         // goals

        // 💡 기존 패스/슈팅 통계 제거, 실시간 집계가 가능한 카드 통계 추가
        private int yellowCards;
        private int redCards;
    }
}
