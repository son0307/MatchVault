package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixturePlayerStatService {

    private final FixtureRecordRepository fixtureRecordRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;

    @Transactional(readOnly = true)
    public FixturePlayerStatResponseDto getFixturePlayerStats(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        List<PlayerFixtureStat> stats = playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId);

        return FixturePlayerStatResponseDto.builder()
                .fixtureId(fixtureId)
                .homeTeam(buildTeamStats(fixture.getHomeTeam(), stats))
                .awayTeam(buildTeamStats(fixture.getAwayTeam(), stats))
                .build();
    }

    private FixturePlayerStatResponseDto.TeamPlayerStats buildTeamStats(Team team, List<PlayerFixtureStat> allStats) {
        List<FixturePlayerStatResponseDto.PlayerStat> players = allStats.stream()
                .filter(stat -> stat.getTeam() != null && team.getId().equals(stat.getTeam().getId()))
                .sorted(Comparator.comparing(stat -> stat.getPlayer().getNumber(), Comparator.nullsLast(Integer::compareTo)))
                .map(this::toPlayerStat)
                .toList();

        return FixturePlayerStatResponseDto.TeamPlayerStats.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .players(players)
                .build();
    }

    private FixturePlayerStatResponseDto.PlayerStat toPlayerStat(PlayerFixtureStat stat) {
        return FixturePlayerStatResponseDto.PlayerStat.builder()
                .playerId(stat.getPlayer().getPlayerId())
                .playerName(stat.getPlayer().getName())
                .jerseyNumber(stat.getPlayer().getNumber())
                .position(stat.getPlayer().getPosition())
                .minutesPlayed(valueOfInt(stat.getMinutesPlayed()))
                .rating(valueOfDouble(stat.getRating()))
                .captain(stat.getIsCaptain())
                .substitute(stat.getIsSubstitute())
                .goals(valueOfInt(stat.getGoals()))
                .assists(valueOfInt(stat.getAssists()))
                .conceded(valueOfInt(stat.getConceded()))
                .saves(valueOfInt(stat.getSaves()))
                .shotsTotal(valueOfInt(stat.getShotsTotal()))
                .shotsOnTarget(valueOfInt(stat.getShotsOnTarget()))
                .passesTotal(valueOfInt(stat.getPassesTotal()))
                .passesKey(valueOfInt(stat.getPassesKey()))
                .passAccuracy(valueOfInt(stat.getPassAccuracy()))
                .tacklesTotal(valueOfInt(stat.getTacklesTotal()))
                .blocks(valueOfInt(stat.getBlocks()))
                .interceptions(valueOfInt(stat.getInterceptions()))
                .duelsTotal(valueOfInt(stat.getDuelsTotal()))
                .duelsWon(valueOfInt(stat.getDuelsWon()))
                .dribblesAttempts(valueOfInt(stat.getDribblesAttempts()))
                .dribblesSuccess(valueOfInt(stat.getDribblesSuccess()))
                .dribblesPast(valueOfInt(stat.getDribblesPast()))
                .foulsDrawn(valueOfInt(stat.getFoulsDrawn()))
                .foulsCommitted(valueOfInt(stat.getFoulsCommitted()))
                .yellowCards(valueOfInt(stat.getYellowCards()))
                .redCards(valueOfInt(stat.getRedCards()))
                .offsides(valueOfInt(stat.getOffsides()))
                .penaltyWon(valueOfInt(stat.getPenaltyWon()))
                .penaltyCommitted(valueOfInt(stat.getPenaltyCommitted()))
                .penaltyScored(valueOfInt(stat.getPenaltyScored()))
                .penaltyMissed(valueOfInt(stat.getPenaltyMissed()))
                .penaltySaved(valueOfInt(stat.getPenaltySaved()))
                .build();
    }

    private int valueOfInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double valueOfDouble(Double value) {
        return value == null ? 0 : value;
    }
}
