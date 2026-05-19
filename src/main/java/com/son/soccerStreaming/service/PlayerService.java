package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import com.son.soccerStreaming.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import com.son.soccerStreaming.repository.PlayerTeamSeasonStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;

    @Transactional(readOnly = true)
    public PlayerResponseDto.Details getPlayerDetails(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        return toDetails(player);
    }

    private PlayerResponseDto.Details toDetails(Player player) {
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
                .build();
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto.Panel getPlayerPanel(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        // 시즌 요약은 API-Football의 시즌 누적 스탯을 저장한 테이블에서 바로 가져온다.
        List<PlayerTeamSeasonStat> seasonStats = playerTeamSeasonStatRepository.findAllByPlayerPlayerIdOrderBySeasonDesc(playerId);
        List<PlayerResponseDto.SeasonSummary> seasons = aggregateSeasonSummaries(seasonStats);

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

    private List<PlayerResponseDto.SeasonSummary> aggregateSeasonSummaries(List<PlayerTeamSeasonStat> stats) {
        Map<Integer, List<PlayerTeamSeasonStat>> statsBySeason = new LinkedHashMap<>();
        for (PlayerTeamSeasonStat stat : stats) {
            statsBySeason.computeIfAbsent(stat.getSeason(), key -> new java.util.ArrayList<>()).add(stat);
        }

        return statsBySeason.entrySet().stream()
                .sorted(Map.Entry.<Integer, List<PlayerTeamSeasonStat>>comparingByKey(Comparator.nullsLast(Comparator.reverseOrder())))
                .map(entry -> toSeasonSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private PlayerResponseDto.SeasonSummary toSeasonSummary(Integer season, List<PlayerTeamSeasonStat> stats) {
        long totalFixtures = stats.stream().mapToLong(item -> valueOf(item.getAppearances())).sum();
        int weightedRatingFixtures = stats.stream()
                .filter(item -> item.getRating() != null && valueOf(item.getAppearances()) > 0)
                .mapToInt(item -> valueOf(item.getAppearances()))
                .sum();
        double weightedRating = stats.stream()
                .filter(item -> item.getRating() != null && valueOf(item.getAppearances()) > 0)
                .mapToDouble(item -> item.getRating() * valueOf(item.getAppearances()))
                .sum();

        return PlayerResponseDto.SeasonSummary.builder()
                .season(season)
                .totalFixtures(totalFixtures)
                .minutesPlayed(stats.stream().mapToInt(item -> valueOf(item.getMinutes())).sum())
                .averageRating(weightedRatingFixtures > 0 ? roundToOneDecimal(weightedRating / weightedRatingFixtures) : 0)
                .goals(stats.stream().mapToInt(item -> valueOf(item.getGoals())).sum())
                .assists(stats.stream().mapToInt(item -> valueOf(item.getAssists())).sum())
                .shots(stats.stream().mapToInt(item -> valueOf(item.getShotsTotal())).sum())
                .shotsOnTarget(stats.stream().mapToInt(item -> valueOf(item.getShotsOnTarget())).sum())
                .keyPasses(stats.stream().mapToInt(item -> valueOf(item.getPassesKey())).sum())
                .yellowCards(stats.stream().mapToInt(item -> valueOf(item.getYellowCards())).sum())
                .redCards(stats.stream().mapToInt(item -> valueOf(item.getRedCards())).sum())
                .teams(stats.stream()
                        .map(this::toTeamSeasonSummary)
                        .toList())
                .build();
    }

    private PlayerResponseDto.TeamSeasonSummary toTeamSeasonSummary(PlayerTeamSeasonStat stats) {
        Team team = stats.getTeam();

        return PlayerResponseDto.TeamSeasonSummary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamLogoUrl(team.getLogoUrl())
                .totalFixtures(valueOf(stats.getAppearances()))
                .minutesPlayed(valueOf(stats.getMinutes()))
                .averageRating(stats.getRating() != null ? roundToOneDecimal(stats.getRating()) : 0)
                .goals(valueOf(stats.getGoals()))
                .assists(valueOf(stats.getAssists()))
                .shots(valueOf(stats.getShotsTotal()))
                .shotsOnTarget(valueOf(stats.getShotsOnTarget()))
                .keyPasses(valueOf(stats.getPassesKey()))
                .yellowCards(valueOf(stats.getYellowCards()))
                .redCards(valueOf(stats.getRedCards()))
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

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
