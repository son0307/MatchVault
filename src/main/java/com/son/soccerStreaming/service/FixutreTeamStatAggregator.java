package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FixutreTeamStatAggregator {

    public FixtureStatResponseDto.TeamStatSummary aggregate(
            Long teamApiId,
            int score,
            List<PlayerFixtureStat> fixtureStats
    ) {
        List<PlayerFixtureStat> teamStats = fixtureStats.stream()
                .filter(stat -> stat.getTeam() != null)
                .filter(stat -> teamApiId.equals(stat.getTeam().getTeamId()))
                .toList();

        int totalShots = teamStats.stream().mapToInt(stat -> valueOf(stat.getShotsTotal())).sum();
        int shotsOnTarget = teamStats.stream().mapToInt(stat -> valueOf(stat.getShotsOnTarget())).sum();
        int totalPasses = teamStats.stream().mapToInt(stat -> valueOf(stat.getPassesTotal())).sum();
        int fouls = teamStats.stream().mapToInt(stat -> valueOf(stat.getFoulsCommitted())).sum();
        int tackles = teamStats.stream().mapToInt(stat -> valueOf(stat.getTacklesTotal())).sum();
        int yellowCards = teamStats.stream().mapToInt(stat -> valueOf(stat.getYellowCards())).sum();
        int redCards = teamStats.stream().mapToInt(stat -> valueOf(stat.getRedCards())).sum();

        double passAccuracy = calculateWeightedPassAccuracy(teamStats, totalPasses);

        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(teamApiId)
                .score(score)
                .totalShots(totalShots)
                .shotsOnTarget(shotsOnTarget)
                .totalPasses(totalPasses)
                .passAccuracy(passAccuracy)
                .fouls(fouls)
                .tackles(tackles)
                .yellowCards(yellowCards)
                .redCards(redCards)
                .build();
    }

    private double calculateWeightedPassAccuracy(List<PlayerFixtureStat> teamStats, int totalPasses) {
        if (totalPasses == 0) {
            return 0;
        }

        int weightedAccuracySum = teamStats.stream()
                .filter(stat -> stat.getPassAccuracy() != null)
                .mapToInt(stat -> valueOf(stat.getPassesTotal()) * stat.getPassAccuracy())
                .sum();

        return Math.round((weightedAccuracySum * 100.0 / totalPasses)) / 100.0;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
