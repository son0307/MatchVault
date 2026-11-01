package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FixtureDetailGenerationReadStore {

    private final JdbcTemplate jdbcTemplate;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureDetailFixtureFieldStore fixtureFieldStore;

    public Optional<Fixture> findFixture(long generationId, long fixtureId) {
        List<Fixture> fixtures = jdbcTemplate.query("""
                SELECT %s FROM fixture_generation f
                JOIN fixture live ON live.id = f.id
                JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                WHERE f.generation_id = ? AND f.fixture_id = ?
                """.formatted(fixtureFieldStore.effectiveColumns()),
                (result, row) -> toFixture(result), generationId, fixtureId);
        if (!fixtures.isEmpty()) {
            return Optional.of(fixtures.get(0));
        }
        Integer targetCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_generation_target
                WHERE generation_id = ? AND fixture_id = ?
                """, Integer.class, generationId, fixtureId);
        if (targetCount != null && targetCount > 0) {
            return Optional.empty(); // A target disappearing from its version is an integrity error.
        }
        return fixtureRepository.findByFixtureId(fixtureId);
    }

    public List<Fixture> findRecentFixtures(List<Long> generationIds, Long cursorId, Integer season,
                                            Integer round, LocalDateTime start, LocalDateTime end,
                                            Long teamId, int size) {
        if (generationIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("generationIds", generationIds)
                .addValue("cursorId", cursorId)
                .addValue("season", season)
                .addValue("round", round)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("teamId", teamId)
                .addValue("limit", size + 1);
        return new NamedParameterJdbcTemplate(jdbcTemplate).query("""
                SELECT effective.* FROM (
                  SELECT %s FROM fixture_generation f
                  JOIN fixture live ON live.id = f.id
                  JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                  WHERE f.generation_id IN (:generationIds)
                  UNION ALL
                  SELECT live.*, active.generation_id FROM fixture live
                  JOIN fixture_detail_active_generation active ON active.season = live.season
                  WHERE active.generation_id IN (:generationIds)
                    AND NOT EXISTS (
                      SELECT 1 FROM fixture_generation f
                      WHERE f.generation_id = active.generation_id AND f.fixture_id = live.fixture_id)
                ) effective
                JOIN team home ON home.id = effective.home_team_id
                JOIN team away ON away.id = effective.away_team_id
                WHERE (:cursorId IS NULL OR effective.id < :cursorId)
                  AND (:season IS NULL OR effective.season = :season)
                  AND (:round IS NULL OR effective.round = :round)
                  AND (:start IS NULL OR effective.fixture_date >= :start)
                  AND (:end IS NULL OR effective.fixture_date < :end)
                  AND (:teamId IS NULL OR home.team_id = :teamId OR away.team_id = :teamId)
                ORDER BY effective.id DESC
                LIMIT :limit
                """.formatted(fixtureFieldStore.effectiveColumns()), parameters,
                (result, row) -> toFixture(result));
    }

    public List<Fixture> findHeadToHead(List<Long> generationIds, int leagueId,
                                        long firstTeamId, long secondTeamId,
                                        List<String> finishedStatuses, int limit) {
        if (generationIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("generationIds", generationIds)
                .addValue("leagueId", leagueId)
                .addValue("firstTeamId", firstTeamId)
                .addValue("secondTeamId", secondTeamId)
                .addValue("finishedStatuses", finishedStatuses)
                .addValue("limit", limit);
        return new NamedParameterJdbcTemplate(jdbcTemplate).query("""
                SELECT effective.* FROM (
                  SELECT %s FROM fixture_generation f
                  JOIN fixture live ON live.id = f.id
                  JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                  WHERE f.generation_id IN (:generationIds)
                  UNION ALL
                  SELECT live.*, active.generation_id FROM fixture live
                  JOIN fixture_detail_active_generation active ON active.season = live.season
                  WHERE active.generation_id IN (:generationIds)
                    AND NOT EXISTS (SELECT 1 FROM fixture_generation f
                      WHERE f.generation_id = active.generation_id AND f.fixture_id = live.fixture_id)
                ) effective
                JOIN team home ON home.id = effective.home_team_id
                JOIN team away ON away.id = effective.away_team_id
                WHERE effective.league_id = :leagueId
                  AND ((home.team_id = :firstTeamId AND away.team_id = :secondTeamId)
                    OR (home.team_id = :secondTeamId AND away.team_id = :firstTeamId))
                  AND effective.home_score IS NOT NULL AND effective.away_score IS NOT NULL
                  AND (effective.fixture_status IN (:finishedStatuses)
                    OR effective.status_short IN (:finishedStatuses))
                ORDER BY effective.fixture_date DESC, effective.fixture_id DESC
                LIMIT :limit
                """.formatted(fixtureFieldStore.effectiveColumns()), parameters,
                (result, row) -> toFixture(result));
    }

    public List<Fixture> searchFixtures(List<Long> generationIds, List<String> tokens, int limit) {
        if (generationIds.isEmpty() || tokens.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("generationIds", generationIds)
                .addValue("limit", limit);
        StringBuilder tokenFilters = new StringBuilder();
        for (int index = 0; index < tokens.size(); index++) {
            String key = "token" + index;
            parameters.addValue(key, "%" + tokens.get(index).toLowerCase(java.util.Locale.ROOT) + "%");
            tokenFilters.append(" AND (LOWER(home.name) LIKE :").append(key)
                    .append(" OR LOWER(home.korean_name) LIKE :").append(key)
                    .append(" OR LOWER(away.name) LIKE :").append(key)
                    .append(" OR LOWER(away.korean_name) LIKE :").append(key).append(")");
        }
        String sql = """
                SELECT effective.* FROM (
                  SELECT %s FROM fixture_generation f
                  JOIN fixture live ON live.id = f.id
                  JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                  WHERE f.generation_id IN (:generationIds)
                  UNION ALL
                  SELECT live.*, active.generation_id FROM fixture live
                  JOIN fixture_detail_active_generation active ON active.season = live.season
                  WHERE active.generation_id IN (:generationIds)
                    AND NOT EXISTS (SELECT 1 FROM fixture_generation f
                      WHERE f.generation_id = active.generation_id AND f.fixture_id = live.fixture_id)
                ) effective
                JOIN team home ON home.id = effective.home_team_id
                JOIN team away ON away.id = effective.away_team_id
                WHERE 1 = 1
                """.formatted(fixtureFieldStore.effectiveColumns())
                + tokenFilters + " ORDER BY effective.fixture_date DESC LIMIT :limit";
        return new NamedParameterJdbcTemplate(jdbcTemplate).query(sql, parameters,
                (result, row) -> toFixture(result));
    }

    public FixtureMeta meta(long generationId, int season) {
        return jdbcTemplate.queryForObject("""
                SELECT MIN(fixture_date) AS min_date, MAX(fixture_date) AS max_date,
                  MAX(CASE WHEN fixture_status IN ('LIVE', 'FINISHED') THEN fixture_date END) AS latest_started_date,
                  MIN(round) AS min_round, MAX(round) AS max_round
                FROM (
                  SELECT effective.fixture_date, effective.fixture_status, effective.round
                  FROM (
                    SELECT %s FROM fixture_generation f
                    JOIN fixture live ON live.id = f.id
                    JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                    WHERE f.generation_id = ? AND f.season = ?
                  ) effective
                  UNION ALL
                  SELECT live.fixture_date, live.fixture_status, live.round FROM fixture live
                  WHERE live.season = ? AND NOT EXISTS (SELECT 1 FROM fixture_generation f
                    WHERE f.generation_id = ? AND f.fixture_id = live.fixture_id)
                ) effective
                """.formatted(fixtureFieldStore.effectiveColumns()),
                (result, row) -> new FixtureMeta(
                timestamp(result, "min_date"), timestamp(result, "max_date"),
                timestamp(result, "latest_started_date"),
                result.getObject("min_round", Integer.class), result.getObject("max_round", Integer.class)),
                generationId, season, season, generationId);
    }

    private LocalDateTime timestamp(ResultSet result, String column) throws SQLException {
        var value = result.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    public record FixtureMeta(LocalDateTime minDate, LocalDateTime maxDate,
                              LocalDateTime latestStartedDate, Integer minRound, Integer maxRound) {
    }

    public List<FixtureEvent> findEvents(long generationId, Fixture fixture) {
        return jdbcTemplate.query("""
                SELECT * FROM fixture_event_generation
                WHERE generation_id = ? AND fixture_id = ?
                ORDER BY elapsed ASC, COALESCE(extra, 0) ASC, event_sequence ASC
                """, (result, row) -> FixtureEvent.builder()
                .id(result.getLong("id"))
                .fixture(fixture)
                .eventSequence(result.getInt("event_sequence"))
                .elapsed(result.getObject("elapsed", Integer.class))
                .extra(result.getObject("extra", Integer.class))
                .team(result.getObject("team_id", Long.class) == null ? null
                        : teamRepository.findById(result.getLong("team_id")).orElse(null))
                .player(result.getObject("player_id", Long.class) == null ? null
                        : playerRepository.findById(result.getLong("player_id")).orElse(null))
                .assistPlayer(result.getObject("assist_player_id", Long.class) == null ? null
                        : playerRepository.findById(result.getLong("assist_player_id")).orElse(null))
                .eventType(result.getString("event_type"))
                .eventDetail(result.getString("event_detail"))
                .comments(result.getString("comments"))
                .build(), generationId, fixture.getId());
    }

    public List<FixtureLineup> findLineups(long generationId, Fixture fixture) {
        return jdbcTemplate.query("""
                SELECT * FROM fixture_lineup_generation
                WHERE generation_id = ? AND fixture_id = ?
                """, (result, row) -> FixtureLineup.builder()
                .id(result.getLong("id"))
                .version(result.getLong("version"))
                .fixture(fixture)
                .team(teamRepository.findById(result.getLong("team_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation lineup team is missing")))
                .player(playerRepository.findById(result.getLong("player_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation lineup player is missing")))
                .jerseyNumber(result.getInt("jersey_number"))
                .position(result.getString("position"))
                .grid(result.getString("grid"))
                .isStarter(result.getBoolean("is_starter"))
                .build(), generationId, fixture.getId());
    }

    public List<FixtureStat> findTeamStats(long generationId, Fixture fixture) {
        return jdbcTemplate.query("""
                SELECT * FROM fixture_stat_generation
                WHERE generation_id = ? AND fixture_id = ?
                """, (result, row) -> FixtureStat.builder()
                .id(result.getLong("id"))
                .version(result.getLong("version"))
                .fixture(fixture)
                .team(teamRepository.findById(result.getLong("team_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation statistic team is missing")))
                .shotsOnGoal(result.getObject("shots_on_goal", Integer.class))
                .shotsOffGoal(result.getObject("shots_off_goal", Integer.class))
                .totalShots(result.getObject("total_shots", Integer.class))
                .blockedShots(result.getObject("blocked_shots", Integer.class))
                .shotsInsideBox(result.getObject("shots_inside_box", Integer.class))
                .shotsOutsideBox(result.getObject("shots_outside_box", Integer.class))
                .fouls(result.getObject("fouls", Integer.class))
                .cornerKicks(result.getObject("corner_kicks", Integer.class))
                .offsides(result.getObject("offsides", Integer.class))
                .ballPossession(result.getObject("ball_possession", Integer.class))
                .yellowCards(result.getObject("yellow_cards", Integer.class))
                .redCards(result.getObject("red_cards", Integer.class))
                .goalkeeperSaves(result.getObject("goalkeeper_saves", Integer.class))
                .totalPasses(result.getObject("total_passes", Integer.class))
                .passesAccurate(result.getObject("passes_accurate", Integer.class))
                .passAccuracy(result.getObject("pass_accuracy", Integer.class))
                .expectedGoals(result.getObject("expected_goals", Double.class))
                .build(), generationId, fixture.getId());
    }

    public List<PlayerFixtureStat> findPlayerStats(long generationId, Fixture fixture) {
        return jdbcTemplate.query("""
                SELECT * FROM player_fixture_stat_generation
                WHERE generation_id = ? AND fixture_id = ?
                """, (result, row) -> toPlayerStat(result, fixture), generationId, fixture.getId());
    }

    public List<PlayerFixtureStat> findPlayerStatsForPlayer(long generationId, long playerId, int season) {
        List<Fixture> fixtures = jdbcTemplate.query("""
                SELECT f.* FROM fixture_generation f
                JOIN player_fixture_stat_generation s
                  ON s.fixture_id = f.id AND s.generation_id = f.generation_id
                JOIN player p ON p.id = s.player_id
                WHERE f.generation_id = ? AND f.season = ? AND p.player_id = ?
                """, (result, row) -> toFixture(result), generationId, season, playerId);
        if (fixtures.isEmpty()) {
            return List.of();
        }
        Map<Long, Fixture> fixtureById = fixtures.stream().collect(Collectors.toMap(Fixture::getId, fixture -> fixture));
        return jdbcTemplate.query("""
                SELECT s.* FROM player_fixture_stat_generation s
                JOIN fixture_generation f ON f.id = s.fixture_id AND f.generation_id = s.generation_id
                JOIN player p ON p.id = s.player_id
                WHERE s.generation_id = ? AND f.season = ? AND p.player_id = ?
                ORDER BY f.fixture_date DESC, f.fixture_id DESC
                """, (result, row) -> toPlayerStat(result, fixtureById.get(result.getLong("fixture_id"))),
                generationId, season, playerId);
    }

    private PlayerFixtureStat toPlayerStat(ResultSet result, Fixture fixture) throws SQLException {
        return PlayerFixtureStat.builder()
                .id(result.getLong("id"))
                .version(result.getLong("version"))
                .fixture(fixture)
                .team(teamRepository.findById(result.getLong("team_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation player statistic team is missing")))
                .player(playerRepository.findById(result.getLong("player_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation statistic player is missing")))
                .minutesPlayed(result.getObject("minutes_played", Integer.class))
                .rating(result.getObject("rating", Double.class))
                .isCaptain(nullableBoolean(result, "is_captain"))
                .isSubstitute(nullableBoolean(result, "is_substitute"))
                .goals(result.getObject("goals", Integer.class))
                .assists(result.getObject("assists", Integer.class))
                .conceded(result.getObject("conceded", Integer.class))
                .saves(result.getObject("saves", Integer.class))
                .shotsTotal(result.getObject("shots_total", Integer.class))
                .shotsOnTarget(result.getObject("shots_on_target", Integer.class))
                .passesTotal(result.getObject("passes_total", Integer.class))
                .passesKey(result.getObject("passes_key", Integer.class))
                .passesAccurate(result.getObject("passes_accurate", Integer.class))
                .passAccuracy(result.getObject("pass_accuracy", Integer.class))
                .tacklesTotal(result.getObject("tackles_total", Integer.class))
                .blocks(result.getObject("blocks", Integer.class))
                .interceptions(result.getObject("interceptions", Integer.class))
                .duelsTotal(result.getObject("duels_total", Integer.class))
                .duelsWon(result.getObject("duels_won", Integer.class))
                .dribblesAttempts(result.getObject("dribbles_attempts", Integer.class))
                .dribblesSuccess(result.getObject("dribbles_success", Integer.class))
                .dribblesPast(result.getObject("dribbles_past", Integer.class))
                .foulsDrawn(result.getObject("fouls_drawn", Integer.class))
                .foulsCommitted(result.getObject("fouls_committed", Integer.class))
                .yellowCards(result.getObject("yellow_cards", Integer.class))
                .redCards(result.getObject("red_cards", Integer.class))
                .offsides(result.getObject("offsides", Integer.class))
                .penaltyWon(result.getObject("penalty_won", Integer.class))
                .penaltyCommitted(result.getObject("penalty_committed", Integer.class))
                .penaltyScored(result.getObject("penalty_scored", Integer.class))
                .penaltyMissed(result.getObject("penalty_missed", Integer.class))
                .penaltySaved(result.getObject("penalty_saved", Integer.class))
                .build();
    }

    private Fixture toFixture(ResultSet result) throws SQLException {
        return Fixture.builder()
                .id(result.getLong("id"))
                .fixtureId(result.getLong("fixture_id"))
                .homeTeam(teamRepository.findById(result.getLong("home_team_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation home team is missing")))
                .awayTeam(teamRepository.findById(result.getLong("away_team_id"))
                        .orElseThrow(() -> new IllegalStateException("Generation away team is missing")))
                .fixtureDate(result.getTimestamp("fixture_date").toLocalDateTime())
                .referee(result.getString("referee"))
                .timezone(result.getString("timezone"))
                .timestamp(result.getObject("timestamp", Long.class))
                .firstPeriod(result.getObject("first_period", Long.class))
                .secondPeriod(result.getObject("second_period", Long.class))
                .round(result.getObject("round", Integer.class))
                .leagueId(result.getInt("league_id"))
                .season(result.getObject("season", Integer.class))
                .venueId(result.getObject("venue_id", Long.class))
                .venueName(result.getString("venue_name"))
                .venueNameKo(result.getString("venue_name_ko"))
                .venueCity(result.getString("venue_city"))
                .statusShort(result.getString("status_short"))
                .statusLong(result.getString("status_long"))
                .elapsed(result.getObject("elapsed", Integer.class))
                .extra(result.getObject("extra", Integer.class))
                .fixtureStatus(result.getString("fixture_status"))
                .homeScore(result.getObject("home_score", Integer.class))
                .awayScore(result.getObject("away_score", Integer.class))
                .homeWinner(nullableBoolean(result, "home_winner"))
                .awayWinner(nullableBoolean(result, "away_winner"))
                .halftimeHomeScore(result.getObject("halftime_home_score", Integer.class))
                .halftimeAwayScore(result.getObject("halftime_away_score", Integer.class))
                .fulltimeHomeScore(result.getObject("fulltime_home_score", Integer.class))
                .fulltimeAwayScore(result.getObject("fulltime_away_score", Integer.class))
                .extratimeHomeScore(result.getObject("extratime_home_score", Integer.class))
                .extratimeAwayScore(result.getObject("extratime_away_score", Integer.class))
                .penaltyHomeScore(result.getObject("penalty_home_score", Integer.class))
                .penaltyAwayScore(result.getObject("penalty_away_score", Integer.class))
                .homeFormation(result.getString("home_formation"))
                .awayFormation(result.getString("away_formation"))
                .homeCoachName(result.getString("home_coach_name"))
                .awayCoachName(result.getString("away_coach_name"))
                .homePlayerColorPrimary(result.getString("home_player_color_primary"))
                .homePlayerColorNumber(result.getString("home_player_color_number"))
                .homePlayerColorBorder(result.getString("home_player_color_border"))
                .homeGoalkeeperColorPrimary(result.getString("home_goalkeeper_color_primary"))
                .homeGoalkeeperColorNumber(result.getString("home_goalkeeper_color_number"))
                .homeGoalkeeperColorBorder(result.getString("home_goalkeeper_color_border"))
                .awayPlayerColorPrimary(result.getString("away_player_color_primary"))
                .awayPlayerColorNumber(result.getString("away_player_color_number"))
                .awayPlayerColorBorder(result.getString("away_player_color_border"))
                .awayGoalkeeperColorPrimary(result.getString("away_goalkeeper_color_primary"))
                .awayGoalkeeperColorNumber(result.getString("away_goalkeeper_color_number"))
                .awayGoalkeeperColorBorder(result.getString("away_goalkeeper_color_border"))
                .build();
    }

    private Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }
}
