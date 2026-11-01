package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FixtureDetailGenerationRankingReadStore {

    private final JdbcTemplate jdbcTemplate;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    public List<PlayerTeamSeasonStat> leagueStats(long generationId, long leagueId, int season) {
        return seasonStats("""
                SELECT * FROM player_team_season_stat_generation
                WHERE generation_id = ? AND league_id = ? AND season = ?
                  AND COALESCE(appearances, 0) > 0
                """, generationId, leagueId, season);
    }

    public List<PlayerTeamSeasonStat> teamStats(long generationId, long teamId, int season) {
        List<PlayerTeamSeasonStat> stats = seasonStats("""
                SELECT season_stat.* FROM player_team_season_stat_generation season_stat
                JOIN team ON team.id = season_stat.team_id
                WHERE season_stat.generation_id = ? AND team.team_id = ? AND season_stat.season = ?
                  AND (COALESCE(season_stat.appearances, 0) > 0
                       OR COALESCE(season_stat.substitutes_bench, 0) > 0)
                """, generationId, teamId, season);
        return stats.stream().sorted(Comparator.comparing(
                stat -> stat.getPlayer().getName(), Comparator.nullsLast(String::compareTo))).toList();
    }

    public List<PlayerTeamSeasonStat> playerSeasonStats(long generationId, long playerId, int season) {
        return seasonStats("""
                SELECT season_stat.* FROM player_team_season_stat_generation season_stat
                JOIN player ON player.id = season_stat.player_id
                WHERE season_stat.generation_id = ? AND player.player_id = ?
                  AND season_stat.season = ?
                """, generationId, playerId, season);
    }

    public List<PlayerFixtureStatRepository.PlayerRankingMatchAggregate> matchAggregates(
            long generationId, int season, List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return named().query("""
                SELECT player.player_id AS player_id,
                  SUM(CASE WHEN COALESCE(stat.minutes_played, 0) > 0 AND COALESCE(stat.goals, 0) > 0
                      THEN 1 ELSE 0 END) AS goal_matches,
                  SUM(CASE WHEN COALESCE(stat.minutes_played, 0) > 0 AND COALESCE(stat.assists, 0) > 0
                      THEN 1 ELSE 0 END) AS assist_matches,
                  SUM(CASE WHEN COALESCE(stat.minutes_played, 0) > 0
                           AND COALESCE(stat.goals, 0) + COALESCE(stat.assists, 0) > 0
                      THEN 1 ELSE 0 END) AS attack_point_matches,
                  SUM(CASE WHEN COALESCE(stat.minutes_played, 0) > 0 AND stat.conceded = 0
                      THEN 1 ELSE 0 END) AS clean_sheets
                FROM player_fixture_stat_generation stat
                JOIN fixture_generation fixture
                  ON fixture.id = stat.fixture_id AND fixture.generation_id = stat.generation_id
                JOIN player ON player.id = stat.player_id
                WHERE stat.generation_id = :generationId AND fixture.season = :season
                  AND player.player_id IN (:playerIds)
                GROUP BY player.player_id
                """, parameters(generationId, season, playerIds), (result, row) ->
                new MatchAggregate(result.getLong("player_id"), result.getLong("goal_matches"),
                        result.getLong("assist_matches"), result.getLong("attack_point_matches"),
                        result.getLong("clean_sheets")));
    }

    public List<PlayerFixtureStatRepository.LatestPlayerTeam> latestTeams(
            long generationId, int season, List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return named().query("""
                SELECT player_id, team_id, fixture_date, fixture_id FROM (
                    SELECT player.player_id, team.team_id, fixture.fixture_date, fixture.fixture_id,
                           ROW_NUMBER() OVER (PARTITION BY player.id
                               ORDER BY fixture.fixture_date DESC, fixture.fixture_id DESC) AS player_match_order
                    FROM player_fixture_stat_generation stat
                    JOIN fixture_generation fixture
                      ON fixture.id = stat.fixture_id AND fixture.generation_id = stat.generation_id
                    JOIN player ON player.id = stat.player_id
                    JOIN team ON team.id = stat.team_id
                    WHERE stat.generation_id = :generationId AND fixture.season = :season
                      AND player.player_id IN (:playerIds)
                ) ranked WHERE player_match_order = 1
                """, parameters(generationId, season, playerIds), (result, row) ->
                new LatestTeam(result.getLong("player_id"), result.getLong("team_id"),
                        result.getTimestamp("fixture_date").toLocalDateTime(), result.getLong("fixture_id")));
    }

    private List<PlayerTeamSeasonStat> seasonStats(String sql, Object... arguments) {
        List<SeasonRow> rows = jdbcTemplate.query(sql, (result, row) -> seasonRow(result), arguments);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> playerIds = rows.stream().map(SeasonRow::playerId).collect(Collectors.toSet());
        Set<Long> teamIds = rows.stream().map(SeasonRow::teamId).collect(Collectors.toSet());
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(player -> players.put(player.getId(), player));
        Map<Long, Team> teams = new HashMap<>();
        teamRepository.findAllById(teamIds).forEach(team -> teams.put(team.getId(), team));
        return rows.stream().map(row -> {
            Player player = players.get(row.playerId());
            Team team = teams.get(row.teamId());
            if (player == null || team == null) {
                throw new IllegalStateException("Published generation has a missing player or team");
            }
            return row.stat().toBuilder().player(player).team(team).build();
        }).toList();
    }

    private SeasonRow seasonRow(ResultSet result) throws SQLException {
        PlayerTeamSeasonStat stat = PlayerTeamSeasonStat.builder()
                .id(result.getLong("id"))
                .leagueId(result.getLong("league_id"))
                .season(result.getInt("season"))
                .backNumber(result.getObject("back_number", Integer.class))
                .position(result.getString("position"))
                .appearances(result.getObject("appearances", Integer.class))
                .lineups(result.getObject("lineups", Integer.class))
                .minutes(result.getObject("minutes", Integer.class))
                .rating(result.getObject("rating", Double.class))
                .captain(nullableBoolean(result, "captain"))
                .substitutesIn(result.getObject("substitutes_in", Integer.class))
                .substitutesOut(result.getObject("substitutes_out", Integer.class))
                .substitutesBench(result.getObject("substitutes_bench", Integer.class))
                .shotsTotal(result.getObject("shots_total", Integer.class))
                .shotsOnTarget(result.getObject("shots_on_target", Integer.class))
                .goals(result.getObject("goals", Integer.class))
                .conceded(result.getObject("conceded", Integer.class))
                .assists(result.getObject("assists", Integer.class))
                .saves(result.getObject("saves", Integer.class))
                .passesTotal(result.getObject("passes_total", Integer.class))
                .passesKey(result.getObject("passes_key", Integer.class))
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
                .yellowRedCards(result.getObject("yellow_red_cards", Integer.class))
                .redCards(result.getObject("red_cards", Integer.class))
                .penaltyWon(result.getObject("penalty_won", Integer.class))
                .penaltyCommitted(result.getObject("penalty_committed", Integer.class))
                .penaltyScored(result.getObject("penalty_scored", Integer.class))
                .penaltyMissed(result.getObject("penalty_missed", Integer.class))
                .penaltySaved(result.getObject("penalty_saved", Integer.class))
                .build();
        return new SeasonRow(result.getLong("player_id"), result.getLong("team_id"), stat);
    }

    private Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }

    private NamedParameterJdbcTemplate named() {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private MapSqlParameterSource parameters(long generationId, int season, List<Long> playerIds) {
        return new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("season", season)
                .addValue("playerIds", playerIds);
    }

    private record SeasonRow(Long playerId, Long teamId, PlayerTeamSeasonStat stat) {
    }

    private record LatestTeam(Long playerId, Long teamId, LocalDateTime fixtureDate, Long fixtureId)
            implements PlayerFixtureStatRepository.LatestPlayerTeam {
        @Override public Long getPlayerId() { return playerId; }
        @Override public Long getTeamId() { return teamId; }
        @Override public LocalDateTime getFixtureDate() { return fixtureDate; }
        @Override public Long getFixtureId() { return fixtureId; }
    }

    private record MatchAggregate(Long playerId, Long goalMatches, Long assistMatches,
                                  Long attackPointMatches, Long cleanSheets)
            implements PlayerFixtureStatRepository.PlayerRankingMatchAggregate {
        @Override public Long getPlayerId() { return playerId; }
        @Override public Long getGoalMatches() { return goalMatches; }
        @Override public Long getAssistMatches() { return assistMatches; }
        @Override public Long getAttackPointMatches() { return attackPointMatches; }
        @Override public Long getCleanSheets() { return cleanSheets; }
    }
}
