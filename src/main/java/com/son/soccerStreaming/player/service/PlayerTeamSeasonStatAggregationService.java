package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerTeamSeasonStatAggregationService {

    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE, allEntries = true)
    })
    @Transactional
    public int rebuildSeason(Integer leagueId, Integer season) {
        log.info("Player team season stat rebuild started. league={}, season={}", leagueId, season);
        List<PlayerFixtureStat> fixtureStats = playerFixtureStatRepository.findAllByFixtureSeason(season);
        fixtureStats.forEach(PlayerFixtureStat::normalizeCards);
        List<FixtureLineup> lineups = fixtureLineupRepository.findAllBySeason(season);
        int rebuiltCount = rebuild(leagueId, season, fixtureStats, lineups);
        log.info(
                "Player team season stat rebuild completed. league={}, season={}, fixtureStats={}, lineups={}, rebuilt={}",
                leagueId,
                season,
                fixtureStats.size(),
                lineups.size(),
                rebuiltCount
        );
        return rebuiltCount;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE, allEntries = true)
    })
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuildForFixture(Integer leagueId, Long fixtureId, Integer season) {
        Set<PlayerTeamSeasonKey> keys = new LinkedHashSet<>();
        playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId).forEach(stat ->
                keys.add(PlayerTeamSeasonKey.from(stat.getPlayer(), stat.getTeam())));
        fixtureLineupRepository.findAllByFixtureId(fixtureId).forEach(lineup ->
                keys.add(PlayerTeamSeasonKey.from(lineup.getPlayer(), lineup.getTeam())));

        int rebuilt = 0;
        for (PlayerTeamSeasonKey key : keys) {
            List<PlayerFixtureStat> fixtureStats =
                    playerFixtureStatRepository.findAllByPlayerPlayerIdAndTeamTeamIdAndFixtureSeason(
                            key.playerId(),
                            key.teamId(),
                            season
                    );
            fixtureStats.forEach(PlayerFixtureStat::normalizeCards);
            List<FixtureLineup> lineups = fixtureLineupRepository.findAllByPlayerTeamAndSeason(
                    key.playerId(),
                    key.teamId(),
                    season
            );
            if (!fixtureStats.isEmpty() || !lineups.isEmpty()) {
                upsert(leagueId, season, key, fixtureStats, lineups);
                rebuilt++;
            }
        }
        return rebuilt;
    }

    private int rebuild(
            Integer leagueId,
            Integer season,
            List<PlayerFixtureStat> fixtureStats,
            List<FixtureLineup> lineups
    ) {
        Map<PlayerTeamSeasonKey, List<PlayerFixtureStat>> fixtureStatsByKey = fixtureStats.stream()
                .collect(Collectors.groupingBy(
                        stat -> PlayerTeamSeasonKey.from(stat.getPlayer(), stat.getTeam()),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));
        Map<PlayerTeamSeasonKey, List<FixtureLineup>> lineupsByKey = lineups.stream()
                .collect(Collectors.groupingBy(
                        lineup -> PlayerTeamSeasonKey.from(lineup.getPlayer(), lineup.getTeam()),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));
        Set<PlayerTeamSeasonKey> keys = new LinkedHashSet<>(fixtureStatsByKey.keySet());
        keys.addAll(lineupsByKey.keySet());

        for (PlayerTeamSeasonKey key : keys) {
            upsert(
                    leagueId,
                    season,
                    key,
                    fixtureStatsByKey.getOrDefault(key, List.of()),
                    lineupsByKey.getOrDefault(key, List.of())
            );
        }
        return keys.size();
    }

    private void upsert(
            Integer leagueId,
            Integer season,
            PlayerTeamSeasonKey key,
            List<PlayerFixtureStat> fixtureStats,
            List<FixtureLineup> lineups
    ) {
        Player player = firstPlayer(fixtureStats, lineups);
        Team team = firstTeam(fixtureStats, lineups);
        PlayerTeamSeasonStat entity = playerTeamSeasonStatRepository
                .findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
                        key.playerId(),
                        key.teamId(),
                        leagueId.longValue(),
                        season
                )
                .orElseGet(() -> PlayerTeamSeasonStat.builder()
                        .player(player)
                        .team(team)
                        .leagueId(leagueId.longValue())
                        .season(season)
                        .build());

        List<PlayerFixtureStat> appearances = fixtureStats.stream()
                .filter(stat -> valueOf(stat.getMinutesPlayed()) > 0)
                .toList();
        FixtureLineup latestLineup = lineups.stream()
                .max(Comparator
                        .comparing((FixtureLineup lineup) -> lineup.getFixture().getFixtureDate(),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(lineup -> lineup.getFixture().getFixtureId(),
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        Set<Long> appearanceFixtureIds = appearances.stream()
                .map(stat -> stat.getFixture().getFixtureId())
                .collect(Collectors.toSet());
        int accuratePasses = sum(appearances, PlayerFixtureStat::getPassesAccurate);
        int totalPasses = sum(appearances, PlayerFixtureStat::getPassesTotal);
        List<Double> ratings = appearances.stream()
                .map(PlayerFixtureStat::getRating)
                .filter(java.util.Objects::nonNull)
                .toList();

        entity.replaceWithFixtureAggregate(
                latestLineup != null ? latestLineup.getJerseyNumber() : null,
                latestLineup != null ? latestLineup.getPosition() : positionFromStats(appearances, player),
                appearances.size(),
                (int) appearances.stream().filter(stat -> !Boolean.TRUE.equals(stat.getIsSubstitute())).count(),
                sum(appearances, PlayerFixtureStat::getMinutesPlayed),
                ratings.isEmpty() ? null : roundToTwoDecimals(ratings.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                appearances.stream().anyMatch(stat -> Boolean.TRUE.equals(stat.getIsCaptain())),
                (int) appearances.stream().filter(stat -> Boolean.TRUE.equals(stat.getIsSubstitute())).count(),
                (int) lineups.stream()
                        .filter(lineup -> !appearanceFixtureIds.contains(lineup.getFixture().getFixtureId()))
                        .count(),
                sum(appearances, PlayerFixtureStat::getShotsTotal),
                sum(appearances, PlayerFixtureStat::getShotsOnTarget),
                sum(appearances, PlayerFixtureStat::getGoals),
                sum(appearances, PlayerFixtureStat::getConceded),
                sum(appearances, PlayerFixtureStat::getAssists),
                sum(appearances, PlayerFixtureStat::getSaves),
                totalPasses,
                sum(appearances, PlayerFixtureStat::getPassesKey),
                totalPasses > 0 ? (int) Math.round(accuratePasses * 100.0 / totalPasses) : null,
                sum(appearances, PlayerFixtureStat::getTacklesTotal),
                sum(appearances, PlayerFixtureStat::getBlocks),
                sum(appearances, PlayerFixtureStat::getInterceptions),
                sum(appearances, PlayerFixtureStat::getDuelsTotal),
                sum(appearances, PlayerFixtureStat::getDuelsWon),
                sum(appearances, PlayerFixtureStat::getDribblesAttempts),
                sum(appearances, PlayerFixtureStat::getDribblesSuccess),
                sum(appearances, PlayerFixtureStat::getDribblesPast),
                sum(appearances, PlayerFixtureStat::getFoulsDrawn),
                sum(appearances, PlayerFixtureStat::getFoulsCommitted),
                sum(appearances, PlayerFixtureStat::getYellowCards),
                sum(appearances, PlayerFixtureStat::getRedCards),
                sum(appearances, PlayerFixtureStat::getPenaltyWon),
                sum(appearances, PlayerFixtureStat::getPenaltyCommitted),
                sum(appearances, PlayerFixtureStat::getPenaltyScored),
                sum(appearances, PlayerFixtureStat::getPenaltyMissed),
                sum(appearances, PlayerFixtureStat::getPenaltySaved)
        );
        playerTeamSeasonStatRepository.save(entity);
    }

    private Player firstPlayer(List<PlayerFixtureStat> fixtureStats, List<FixtureLineup> lineups) {
        if (!fixtureStats.isEmpty()) {
            return fixtureStats.get(0).getPlayer();
        }
        return lineups.get(0).getPlayer();
    }

    private Team firstTeam(List<PlayerFixtureStat> fixtureStats, List<FixtureLineup> lineups) {
        if (!fixtureStats.isEmpty()) {
            return fixtureStats.get(0).getTeam();
        }
        return lineups.get(0).getTeam();
    }

    private String positionFromStats(List<PlayerFixtureStat> stats, Player player) {
        return player.getPosition();
    }

    private int sum(List<PlayerFixtureStat> stats, Function<PlayerFixtureStat, Integer> extractor) {
        return stats.stream().map(extractor).mapToInt(this::valueOf).sum();
    }

    private int valueOf(Integer value) {
        return value != null ? value : 0;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PlayerTeamSeasonKey(Long playerId, Long teamId) {
        private static PlayerTeamSeasonKey from(Player player, Team team) {
            return new PlayerTeamSeasonKey(player.getPlayerId(), team.getTeamId());
        }
    }
}
