package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;

    @Transactional(readOnly = true)
    public PlayerResponseDto.Details getPlayerDetails(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        return toDetails(player);
    }

    private PlayerResponseDto.Details toDetails(Player player) {
        Team team = player.getTeam();
        return PlayerResponseDto.Details.builder()
                .playerId(player.getPlayerId())
                .playerName(player.getName())
                .firstname(player.getFirstname())
                .lastname(player.getLastname())
                .backNumber(player.getNumber())
                .age(player.getAge())
                .birthDate(player.getBirthDate())
                .birthPlace(player.getBirthPlace())
                .birthCountry(player.getBirthCountry())
                .nationality(player.getNationality())
                .height(player.getHeight())
                .weight(player.getWeight())
                .position(player.getPosition())
                .photoUrl(player.getPhotoUrl())
                .teamId(team != null ? team.getTeamId() : null)
                .teamName(team != null ? team.getName() : null)
                .teamLogoUrl(team != null ? team.getLogoUrl() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto.Panel getPlayerPanel(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        // 선수 패널은 한 번의 요청으로 프로필, 시즌 요약, 경기별 핵심 기록을 함께 렌더링한다.
        List<PlayerResponseDto.SeasonSummary> seasons = playerFixtureStatRepository
                .findSeasonStatSummariesByPlayerId(playerId)
                .stream()
                .map(this::toSeasonSummary)
                .toList();

        List<PlayerResponseDto.MatchStat> matches = playerFixtureStatRepository
                .findAllByPlayerPlayerIdOrderByFixtureFixtureDateDesc(playerId)
                .stream()
                .map(this::toMatchStat)
                .toList();

        return PlayerResponseDto.Panel.builder()
                .profile(toDetails(player))
                .seasons(seasons)
                .matches(matches)
                .build();
    }

    @Cacheable(value = "playerStats", key = "#playerId")
    @Transactional(readOnly = true)
    public PlayerResponseDto.SeasonStats getPlayerSeasonStats(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        PlayerFixtureStatRepository.SeasonStatSummary stats =
                playerFixtureStatRepository.findSeasonStatSummaryByPlayerId(playerId);

        return PlayerResponseDto.SeasonStats.builder()
                .playerId(player.getPlayerId())
                .totalFixtures(stats.getTotalFixtures())
                .minutesPlayed(stats.getMinutesPlayed())
                .averageRating(roundToOneDecimal(stats.getAverageRating()))
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .conceded(stats.getConceded())
                .saves(stats.getSaves())
                .shots(stats.getShotsTotal())
                .shotsOnTarget(stats.getShotsOnTarget())
                .totalPasses(stats.getPassesTotal())
                .keyPasses(stats.getPassesKey())
                .passAccuracy(roundToOneDecimal(stats.getAvgPassAccuracy()))
                .foulsDrawn(stats.getFoulsDrawn())
                .foulsCommitted(stats.getFoulsCommitted())
                .tackles(stats.getTacklesTotal())
                .blocks(stats.getBlocks())
                .interceptions(stats.getInterceptions())
                .duelsTotal(stats.getDuelsTotal())
                .duelsWon(stats.getDuelsWon())
                .dribblesAttempts(stats.getDribblesAttempts())
                .dribblesSuccess(stats.getDribblesSuccess())
                .dribblesPast(stats.getDribblesPast())
                .yellowCards(stats.getYellowCards())
                .redCards(stats.getRedCards())
                .offsides(stats.getOffsides())
                .penaltyWon(stats.getPenaltyWon())
                .penaltyCommitted(stats.getPenaltyCommitted())
                .penaltyScored(stats.getPenaltyScored())
                .penaltyMissed(stats.getPenaltyMissed())
                .penaltySaved(stats.getPenaltySaved())
                .build();
    }

    private PlayerResponseDto.SeasonSummary toSeasonSummary(
            PlayerFixtureStatRepository.SeasonStatBySeason stats
    ) {
        return PlayerResponseDto.SeasonSummary.builder()
                .season(stats.getSeason())
                .totalFixtures(stats.getTotalFixtures())
                .minutesPlayed(stats.getMinutesPlayed())
                .averageRating(roundToOneDecimal(stats.getAverageRating()))
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .shots(stats.getShotsTotal())
                .shotsOnTarget(stats.getShotsOnTarget())
                .keyPasses(stats.getPassesKey())
                .yellowCards(stats.getYellowCards())
                .redCards(stats.getRedCards())
                .build();
    }

    private PlayerResponseDto.MatchStat toMatchStat(PlayerFixtureStat stat) {
        Fixture fixture = stat.getFixture();
        Team team = stat.getTeam();
        Team opponent = opponentOf(fixture, team);

        return PlayerResponseDto.MatchStat.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(fixture.getFixtureDate())
                .season(fixture.getSeason())
                .round(fixture.getRound())
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .opponentTeamId(opponent != null ? opponent.getTeamId() : null)
                .opponentTeamName(opponent != null ? opponent.getName() : null)
                .teamScore(scoreOf(fixture, team))
                .opponentScore(opponent != null ? scoreOf(fixture, opponent) : null)
                .minutesPlayed(stat.getMinutesPlayed())
                .rating(stat.getRating() != null ? roundToOneDecimal(stat.getRating()) : null)
                .goals(stat.getGoals())
                .assists(stat.getAssists())
                .build();
    }

    private Team opponentOf(Fixture fixture, Team team) {
        if (fixture.getHomeTeam().getId().equals(team.getId())) {
            return fixture.getAwayTeam();
        }
        if (fixture.getAwayTeam().getId().equals(team.getId())) {
            return fixture.getHomeTeam();
        }
        return null;
    }

    private Integer scoreOf(Fixture fixture, Team team) {
        if (fixture.getHomeTeam().getId().equals(team.getId())) {
            return fixture.getHomeScore();
        }
        if (fixture.getAwayTeam().getId().equals(team.getId())) {
            return fixture.getAwayScore();
        }
        return null;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
