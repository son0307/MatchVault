package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchPlayerStatResponseDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.entity.PlayerMatchStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchPlayerStatService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    @Transactional(readOnly = true)
    public MatchPlayerStatResponseDto getPlayerStats(Long fixtureId) {
        MatchRecord match = matchRecordRepository.findByApiFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));
        List<PlayerMatchStat> stats = playerMatchStatRepository.findAllByMatchRecordApiFixtureId(fixtureId);

        return MatchPlayerStatResponseDto.builder()
                .matchId(fixtureId)
                .homeTeam(buildTeamStats(match.getHomeTeam(), stats))
                .awayTeam(buildTeamStats(match.getAwayTeam(), stats))
                .build();
    }

    private MatchPlayerStatResponseDto.TeamPlayerStats buildTeamStats(Team team, List<PlayerMatchStat> allStats) {
        List<MatchPlayerStatResponseDto.PlayerStat> players = allStats.stream()
                .filter(stat -> stat.getTeam() != null && team.getId().equals(stat.getTeam().getId()))
                .sorted(Comparator.comparing(stat -> stat.getPlayer().getDefaultNumber(), Comparator.nullsLast(Integer::compareTo)))
                .map(this::toPlayerStat)
                .toList();

        return MatchPlayerStatResponseDto.TeamPlayerStats.builder()
                .teamId(team.getTeamApiId())
                .teamName(team.getName())
                .players(players)
                .build();
    }

    private MatchPlayerStatResponseDto.PlayerStat toPlayerStat(PlayerMatchStat stat) {
        return MatchPlayerStatResponseDto.PlayerStat.builder()
                .playerId(stat.getPlayer().getApiPlayerId())
                .playerName(stat.getPlayer().getName())
                .jerseyNumber(stat.getPlayer().getDefaultNumber())
                .position(stat.getPlayer().getPosition())
                .minutesPlayed(stat.getMinutesPlayed())
                .rating(stat.getRating())
                .captain(stat.getIsCaptain())
                .substitute(stat.getIsSubstitute())
                .goals(stat.getGoals())
                .assists(stat.getAssists())
                .conceded(stat.getConceded())
                .saves(stat.getSaves())
                .shotsTotal(stat.getShotsTotal())
                .shotsOnTarget(stat.getShotsOnTarget())
                .passesTotal(stat.getPassesTotal())
                .passesKey(stat.getPassesKey())
                .passAccuracy(stat.getPassAccuracy())
                .tacklesTotal(stat.getTacklesTotal())
                .blocks(stat.getBlocks())
                .interceptions(stat.getInterceptions())
                .duelsTotal(stat.getDuelsTotal())
                .duelsWon(stat.getDuelsWon())
                .dribblesAttempts(stat.getDribblesAttempts())
                .dribblesSuccess(stat.getDribblesSuccess())
                .dribblesPast(stat.getDribblesPast())
                .foulsDrawn(stat.getFoulsDrawn())
                .foulsCommitted(stat.getFoulsCommitted())
                .yellowCards(stat.getYellowCards())
                .redCards(stat.getRedCards())
                .offsides(stat.getOffsides())
                .penaltyWon(stat.getPenaltyWon())
                .penaltyCommitted(stat.getPenaltyCommitted())
                .penaltyScored(stat.getPenaltyScored())
                .penaltyMissed(stat.getPenaltyMissed())
                .penaltySaved(stat.getPenaltySaved())
                .build();
    }
}
