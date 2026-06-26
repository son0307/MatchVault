package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.player.dto.PlayerResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.util.DateTimeUtils;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final MediaUrlService mediaUrlService;

    @Transactional(readOnly = true)
    public PlayerResponseDto.Details getPlayerDetails(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        Team latestTeam = playerFixtureStatRepository
                .findTopByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(playerId)
                .map(PlayerFixtureStat::getTeam)
                .orElse(null);

        return toDetails(player, latestTeam);
    }

    private PlayerResponseDto.Details toDetails(Player player, Team latestTeam) {
        return PlayerResponseDto.Details.builder()
                .playerId(player.getPlayerId())
                .playerName(player.getName())
                .playerNameKo(player.getKoreanName())
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
                .photoUrl(mediaUrlService.playerPhotoUrl(player))
                .teamId(latestTeam != null ? latestTeam.getTeamId() : null)
                .teamName(latestTeam != null ? latestTeam.getName() : null)
                .teamNameKo(latestTeam != null ? latestTeam.getKoreanName() : null)
                .teamLogoUrl(mediaUrlService.teamLogoUrl(latestTeam))
                .build();
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto.Panel getPlayerPanel(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        // 시즌 요약은 API-Football의 시즌 누적 스탯을 저장한 테이블에서 바로 가져온다.
        List<PlayerFixtureStat> matchStats = playerFixtureStatRepository
                .findAllByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(playerId);
        Team latestTeam = matchStats.stream()
                .findFirst()
                .map(PlayerFixtureStat::getTeam)
                .orElse(null);
        List<PlayerResponseDto.MatchStat> matches = matchStats
                .stream()
                .map(this::toMatchStat)
                .toList();

        List<PlayerTeamSeasonStat> seasonStats = playerTeamSeasonStatRepository.findAllByPlayerPlayerIdOrderBySeasonDesc(playerId);
        List<PlayerResponseDto.SeasonSummary> seasons = aggregateSeasonSummaries(
                seasonStats,
                teamsBySeasonFromMatches(matches),
                cleanSheetsBySeasonAndTeam(matchStats)
        );

        return PlayerResponseDto.Panel.builder()
                .profile(toDetails(player, latestTeam))
                .seasons(seasons)
                .matches(matches)
                .build();
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto.SeasonSummary getPlayerSeasonSummary(Long playerId, Integer season) {
        ensurePlayerExists(playerId);

        List<PlayerFixtureStat> matchStats = playerFixtureStatRepository
                .findAllByPlayerPlayerIdAndFixtureSeasonOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(playerId, season);
        List<PlayerResponseDto.MatchStat> matches = matchStats
                .stream()
                .map(this::toMatchStat)
                .toList();
        List<PlayerTeamSeasonStat> seasonStats = playerTeamSeasonStatRepository.findAllByPlayerPlayerIdAndSeason(playerId, season);
        List<PlayerResponseDto.SeasonSummary> summaries = aggregateSeasonSummaries(
                seasonStats,
                teamsBySeasonFromMatches(matches),
                cleanSheetsBySeasonAndTeam(matchStats)
        );

        return summaries.stream()
                .findFirst()
                .orElseGet(() -> emptySeasonSummary(season));
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto.MatchStat> getPlayerRecentMatches(Long playerId, Integer season, int size) {
        ensurePlayerExists(playerId);
        int limit = Math.max(1, Math.min(size, 30));

        return playerFixtureStatRepository
                .findAllByPlayerPlayerIdAndFixtureSeasonOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(playerId, season)
                .stream()
                .limit(limit)
                .map(this::toMatchStat)
                .toList();
    }

    private void ensurePlayerExists(Long playerId) {
        if (!playerRepository.existsByPlayerId(playerId)) {
            throw new CustomException(ErrorCode.PLAYER_NOT_FOUND);
        }
    }

    private Map<Integer, Set<Long>> teamsBySeasonFromMatches(List<PlayerResponseDto.MatchStat> matches) {
        Map<Integer, Set<Long>> teamsBySeason = new HashMap<>();
        for (PlayerResponseDto.MatchStat match : matches) {
            if (match.getSeason() == null || match.getTeamId() == null) {
                continue;
            }
            teamsBySeason.computeIfAbsent(match.getSeason(), key -> new HashSet<>()).add(match.getTeamId());
        }
        return teamsBySeason;
    }

    private List<PlayerResponseDto.SeasonSummary> aggregateSeasonSummaries(
            List<PlayerTeamSeasonStat> stats,
            Map<Integer, Set<Long>> teamsBySeasonFromMatches,
            Map<Integer, Map<Long, Integer>> cleanSheetsBySeasonAndTeam
    ) {
        Map<Integer, List<PlayerTeamSeasonStat>> statsBySeason = new LinkedHashMap<>();
        for (PlayerTeamSeasonStat stat : stats) {
            Set<Long> matchTeamIds = teamsBySeasonFromMatches.get(stat.getSeason());
            if (matchTeamIds != null && !matchTeamIds.isEmpty()
                    && !matchTeamIds.contains(stat.getTeam().getTeamId())) {
                continue;
            }
            statsBySeason.computeIfAbsent(stat.getSeason(), key -> new java.util.ArrayList<>()).add(stat);
        }

        return statsBySeason.entrySet().stream()
                .sorted(Map.Entry.<Integer, List<PlayerTeamSeasonStat>>comparingByKey(Comparator.nullsLast(Comparator.reverseOrder())))
                .map(entry -> toSeasonSummary(
                        entry.getKey(),
                        entry.getValue(),
                        cleanSheetsBySeasonAndTeam.getOrDefault(entry.getKey(), Map.of())
                ))
                .toList();
    }

    private PlayerResponseDto.SeasonSummary toSeasonSummary(
            Integer season,
            List<PlayerTeamSeasonStat> stats,
            Map<Long, Integer> cleanSheetsByTeam
    ) {
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
                .cleanSheets(cleanSheetsByTeam.values().stream().mapToInt(Integer::intValue).sum())
                .conceded(stats.stream().mapToInt(item -> valueOf(item.getConceded())).sum())
                .saves(stats.stream().mapToInt(item -> valueOf(item.getSaves())).sum())
                .goals(stats.stream().mapToInt(item -> valueOf(item.getGoals())).sum())
                .assists(stats.stream().mapToInt(item -> valueOf(item.getAssists())).sum())
                .shots(stats.stream().mapToInt(item -> valueOf(item.getShotsTotal())).sum())
                .shotsOnTarget(stats.stream().mapToInt(item -> valueOf(item.getShotsOnTarget())).sum())
                .keyPasses(stats.stream().mapToInt(item -> valueOf(item.getPassesKey())).sum())
                .yellowCards(stats.stream().mapToInt(item -> valueOf(item.getYellowCards())).sum())
                .redCards(stats.stream().mapToInt(item -> valueOf(item.getRedCards())).sum())
                .teams(stats.stream()
                        .map(item -> toTeamSeasonSummary(
                                item,
                                cleanSheetsByTeam.getOrDefault(item.getTeam().getTeamId(), 0)
                        ))
                        .toList())
                .build();
    }

    private PlayerResponseDto.SeasonSummary emptySeasonSummary(Integer season) {
        return PlayerResponseDto.SeasonSummary.builder()
                .season(season)
                .totalFixtures(0)
                .minutesPlayed(0)
                .averageRating(0)
                .cleanSheets(0)
                .conceded(0)
                .saves(0)
                .goals(0)
                .assists(0)
                .shots(0)
                .shotsOnTarget(0)
                .keyPasses(0)
                .yellowCards(0)
                .redCards(0)
                .teams(List.of())
                .build();
    }

    private PlayerResponseDto.TeamSeasonSummary toTeamSeasonSummary(PlayerTeamSeasonStat stats, int cleanSheets) {
        Team team = stats.getTeam();

        return PlayerResponseDto.TeamSeasonSummary.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .teamLogoUrl(mediaUrlService.teamLogoUrl(team))
                .totalFixtures(valueOf(stats.getAppearances()))
                .minutesPlayed(valueOf(stats.getMinutes()))
                .averageRating(stats.getRating() != null ? roundToOneDecimal(stats.getRating()) : 0)
                .cleanSheets(cleanSheets)
                .conceded(valueOf(stats.getConceded()))
                .saves(valueOf(stats.getSaves()))
                .goals(valueOf(stats.getGoals()))
                .assists(valueOf(stats.getAssists()))
                .shots(valueOf(stats.getShotsTotal()))
                .shotsOnTarget(valueOf(stats.getShotsOnTarget()))
                .keyPasses(valueOf(stats.getPassesKey()))
                .yellowCards(valueOf(stats.getYellowCards()))
                .redCards(valueOf(stats.getRedCards()))
                .build();
    }

    private Map<Integer, Map<Long, Integer>> cleanSheetsBySeasonAndTeam(List<PlayerFixtureStat> matchStats) {
        Map<Integer, Map<Long, Integer>> cleanSheets = new HashMap<>();
        for (PlayerFixtureStat stat : matchStats) {
            Integer season = stat.getFixture().getSeason();
            Long teamId = stat.getTeam().getTeamId();
            if (season == null || teamId == null
                    || valueOf(stat.getMinutesPlayed()) <= 0
                    || stat.getConceded() == null
                    || stat.getConceded() != 0) {
                continue;
            }
            cleanSheets
                    .computeIfAbsent(season, key -> new HashMap<>())
                    .merge(teamId, 1, Integer::sum);
        }
        return cleanSheets;
    }

    private PlayerResponseDto.MatchStat toMatchStat(PlayerFixtureStat stat) {
        Fixture fixture = stat.getFixture();
        Team team = stat.getTeam();
        Team opponent = opponentOf(fixture, team);

        return PlayerResponseDto.MatchStat.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .season(fixture.getSeason())
                .round(fixture.getRound())
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .teamNameKo(team.getKoreanName())
                .opponentTeamId(opponent != null ? opponent.getTeamId() : null)
                .opponentTeamName(opponent != null ? opponent.getName() : null)
                .opponentTeamNameKo(opponent != null ? opponent.getKoreanName() : null)
                .teamScore(scoreOf(fixture, team))
                .opponentScore(opponent != null ? scoreOf(fixture, opponent) : null)
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .minutesPlayed(PlayerFixtureStat.normalizeMinutesPlayed(stat.getMinutesPlayed()))
                .rating(stat.getRating() != null ? roundToOneDecimal(stat.getRating()) : null)
                .goals(PlayerFixtureStat.normalizeScoringStat(stat.getMinutesPlayed(), stat.getGoals()))
                .assists(PlayerFixtureStat.normalizeScoringStat(stat.getMinutesPlayed(), stat.getAssists()))
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
