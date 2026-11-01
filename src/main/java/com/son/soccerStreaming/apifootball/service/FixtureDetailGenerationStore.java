package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent lifecycle for a season detail generation. It does not change the active pointer during preparation. */
@Repository
@RequiredArgsConstructor
public class FixtureDetailGenerationStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FixtureDetailFixtureFieldStore fixtureFieldStore;

    @Transactional
    public long create(int season, List<Long> fixtureIds) {
        Set<Long> uniqueIds = new HashSet<>(fixtureIds);
        if (uniqueIds.size() != fixtureIds.size() || uniqueIds.contains(null)) {
            throw new IllegalArgumentException("Generation targets must contain distinct non-null fixture IDs");
        }
        jdbcTemplate.update("INSERT IGNORE INTO fixture_detail_season_lock (season) VALUES (?)", season);
        jdbcTemplate.queryForList("SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, season);
        // A worker retry starts a fresh private generation; a stale writer will fail its status check.
        jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET status = 'FAILED'
                WHERE season = ? AND status IN ('BUILDING', 'READY')
                """, season);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO fixture_detail_generation (season, status, expected_fixture_count, start_fixture_revision) "
                            + "SELECT ?, 'BUILDING', ?, fixture_revision FROM fixture_detail_season_lock WHERE season = ?",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, season);
            statement.setInt(2, fixtureIds.size());
            statement.setInt(3, season);
            return statement;
        }, keyHolder);
        long generationId = keyHolder.getKey().longValue();
        for (Long fixtureId : fixtureIds) {
            jdbcTemplate.update("INSERT INTO fixture_detail_generation_target (generation_id, fixture_id) VALUES (?, ?)",
                    generationId, fixtureId);
        }
        return generationId;
    }

    @Transactional
    public void stageChunk(long generationId, List<Long> requestedIds,
                           List<ApiFootballLiveDto.FixtureResponse> responses) {
        if (requestedIds == null || responses == null || requestedIds.size() != responses.size()) {
            throw new IllegalArgumentException("Fixture detail chunk response count does not match the request");
        }
        Set<Long> expected = new HashSet<>(requestedIds);
        Set<Long> received = new HashSet<>();
        for (ApiFootballLiveDto.FixtureResponse response : responses) {
            Long fixtureId = response != null && response.getFixture() != null
                    ? response.getFixture().getId() : null;
            if (fixtureId == null || !expected.contains(fixtureId) || !received.add(fixtureId)) {
                throw new IllegalArgumentException("Fixture detail chunk contains an unexpected or duplicate fixture ID");
            }
        }
        if (expected.size() != requestedIds.size() || !received.equals(expected)) {
            throw new IllegalArgumentException("Fixture detail chunk omitted or duplicated a requested fixture ID");
        }
        Map<Long, String> payloads = new LinkedHashMap<>();
        for (ApiFootballLiveDto.FixtureResponse response : responses) {
            try {
                payloads.put(response.getFixture().getId(), objectMapper.writeValueAsString(response));
            } catch (JacksonException exception) {
                throw new IllegalArgumentException("Could not serialize fixture detail response", exception);
            }
        }
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status)) {
            throw new IllegalStateException("Only a building generation can receive fixture responses: " + generationId);
        }
        copyPublishedChunk(generationId, requestedIds);
        for (Map.Entry<Long, String> entry : payloads.entrySet()) {
            jdbcTemplate.update("""
                    INSERT INTO fixture_detail_generation_response (generation_id, fixture_id, payload)
                    VALUES (?, ?, CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE payload = CAST(? AS JSON), received_at = CURRENT_TIMESTAMP(6)
                    """, generationId, entry.getKey(), entry.getValue(), entry.getValue());
        }
    }

    private void copyPublishedChunk(long generationId, List<Long> fixtureIds) {
        if (fixtureIds.isEmpty()) {
            return;
        }
        Integer season = jdbcTemplate.queryForObject(
                "SELECT season FROM fixture_detail_generation WHERE id = ?", Integer.class, generationId);
        Long activeGenerationId = activeGenerationId(season);
        String placeholders = String.join(",", Collections.nCopies(fixtureIds.size(), "?"));
        String columns = copyColumns("fixture", null);
        String sourceColumns = copyColumns("fixture", "source");
        int copied = 0;
        if (activeGenerationId != null) {
            List<Object> versionParameters = new ArrayList<>(List.of(generationId, activeGenerationId));
            versionParameters.addAll(fixtureIds);
            copied = jdbcTemplate.update("""
                    INSERT INTO fixture_generation (%s, generation_id)
                    SELECT %s, ? FROM (
                      SELECT %s FROM fixture_generation f
                      JOIN fixture live ON live.id = f.id
                      JOIN fixture_detail_season_lock season_lock ON season_lock.season = f.season
                      WHERE f.generation_id = ? AND f.fixture_id IN (%s)
                    ) source
                    """.formatted(columns, sourceColumns, fixtureFieldStore.effectiveColumns(), placeholders),
                    versionParameters.toArray());
        }
        List<Object> baseParameters = new ArrayList<>();
        baseParameters.add(generationId);
        baseParameters.addAll(fixtureIds);
        if (activeGenerationId == null) {
            copied += jdbcTemplate.update("""
                    INSERT INTO fixture_generation (%s, generation_id)
                    SELECT %s, ? FROM fixture source
                    WHERE source.fixture_id IN (%s)
                    """.formatted(columns, sourceColumns, placeholders), baseParameters.toArray());
        } else {
            baseParameters.add(activeGenerationId);
            copied += jdbcTemplate.update("""
                    INSERT INTO fixture_generation (%s, generation_id)
                    SELECT %s, ? FROM fixture source
                    WHERE source.fixture_id IN (%s)
                      AND NOT EXISTS (SELECT 1 FROM fixture_generation old
                        WHERE old.generation_id = ? AND old.id = source.id)
                    """.formatted(columns, sourceColumns, placeholders), baseParameters.toArray());
        }
        if (copied != fixtureIds.size()) {
            throw new IllegalStateException("Published fixture row disappeared during generation preparation");
        }
        copyChildRows("fixture_event", placeholders, generationId, activeGenerationId, fixtureIds);
        copyChildRows("fixture_lineup", placeholders, generationId, activeGenerationId, fixtureIds);
        copyChildRows("fixture_stat", placeholders, generationId, activeGenerationId, fixtureIds);
        copyChildRows("player_fixture_stat", placeholders, generationId, activeGenerationId, fixtureIds);
    }

    private void copyChildRows(String table, String placeholders, long generationId,
                               Long activeGenerationId, List<Long> fixtureIds) {
        String columns = copyColumns(table, null);
        String sourceColumns = copyColumns(table, "source");
        if (activeGenerationId != null) {
            List<Object> versionParameters = new ArrayList<>(List.of(generationId, activeGenerationId));
            versionParameters.addAll(fixtureIds);
            jdbcTemplate.update("""
                    INSERT INTO %s_generation (%s, generation_id)
                    SELECT %s, ? FROM %s_generation source
                    JOIN fixture_generation f ON f.id = source.fixture_id
                      AND f.generation_id = source.generation_id
                    WHERE source.generation_id = ? AND f.fixture_id IN (%s)
                    """.formatted(table, columns, sourceColumns, table, placeholders),
                    versionParameters.toArray());
        }
        List<Object> baseParameters = new ArrayList<>();
        baseParameters.add(generationId);
        baseParameters.addAll(fixtureIds);
        if (activeGenerationId == null) {
            jdbcTemplate.update("""
                    INSERT INTO %s_generation (%s, generation_id)
                    SELECT %s, ? FROM %s source
                    JOIN fixture f ON f.id = source.fixture_id
                    WHERE f.fixture_id IN (%s)
                    """.formatted(table, columns, sourceColumns, table, placeholders), baseParameters.toArray());
        } else {
            baseParameters.add(activeGenerationId);
            jdbcTemplate.update("""
                    INSERT INTO %s_generation (%s, generation_id)
                    SELECT %s, ? FROM %s source
                    JOIN fixture f ON f.id = source.fixture_id
                    WHERE f.fixture_id IN (%s)
                      AND NOT EXISTS (SELECT 1 FROM fixture_generation old
                        WHERE old.generation_id = ? AND old.id = f.id)
                    """.formatted(table, columns, sourceColumns, table, placeholders), baseParameters.toArray());
        }
    }

    private String copyColumns(String table, String alias) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name <> 'generation_id'
                ORDER BY ordinal_position
                """, String.class, table);
        if (columns.isEmpty()) {
            throw new IllegalStateException("Fixture detail source table is missing: " + table);
        }
        return columns.stream().map(column -> {
            if (!column.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Unexpected fixture detail column name");
            }
            return (alias == null ? "" : alias + ".") + "`" + column + "`";
        }).collect(java.util.stream.Collectors.joining(", "));
    }

    @Transactional
    public void applyFixtureResponse(long generationId, Fixture draft) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status)) {
            throw new IllegalStateException("Only a building generation can change fixture rows: " + generationId);
        }
        int updated = jdbcTemplate.update("""
                UPDATE fixture_generation SET
                    fixture_date = ?, referee = ?, timezone = ?, timestamp = ?,
                    first_period = ?, second_period = ?, round = ?, league_id = ?,
                    venue_id = ?, venue_name = ?, venue_city = ?,
                    status_short = ?, status_long = ?, elapsed = ?, extra = ?, fixture_status = ?,
                    home_score = ?, away_score = ?, home_winner = ?, away_winner = ?,
                    halftime_home_score = ?, halftime_away_score = ?,
                    fulltime_home_score = ?, fulltime_away_score = ?,
                    extratime_home_score = ?, extratime_away_score = ?,
                    penalty_home_score = ?, penalty_away_score = ?,
                    home_formation = ?, away_formation = ?,
                    home_coach_name = ?, away_coach_name = ?,
                    home_player_color_primary = ?, home_player_color_number = ?,
                    home_player_color_border = ?,
                    home_goalkeeper_color_primary = ?, home_goalkeeper_color_number = ?,
                    home_goalkeeper_color_border = ?,
                    away_player_color_primary = ?, away_player_color_number = ?,
                    away_player_color_border = ?,
                    away_goalkeeper_color_primary = ?, away_goalkeeper_color_number = ?,
                    away_goalkeeper_color_border = ?
                WHERE generation_id = ? AND fixture_id = ? AND season = ?
                """,
                draft.getFixtureDate(), draft.getReferee(), draft.getTimezone(), draft.getTimestamp(),
                draft.getFirstPeriod(), draft.getSecondPeriod(), draft.getRound(), draft.getLeagueId(),
                draft.getVenueId(), draft.getVenueName(), draft.getVenueCity(),
                draft.getStatusShort(), draft.getStatusLong(), draft.getElapsed(), draft.getExtra(),
                draft.getFixtureStatus(), draft.getHomeScore(), draft.getAwayScore(),
                draft.getHomeWinner(), draft.getAwayWinner(),
                draft.getHalftimeHomeScore(), draft.getHalftimeAwayScore(),
                draft.getFulltimeHomeScore(), draft.getFulltimeAwayScore(),
                draft.getExtratimeHomeScore(), draft.getExtratimeAwayScore(),
                draft.getPenaltyHomeScore(), draft.getPenaltyAwayScore(),
                draft.getHomeFormation(), draft.getAwayFormation(),
                draft.getHomeCoachName(), draft.getAwayCoachName(),
                draft.getHomePlayerColorPrimary(), draft.getHomePlayerColorNumber(),
                draft.getHomePlayerColorBorder(),
                draft.getHomeGoalkeeperColorPrimary(), draft.getHomeGoalkeeperColorNumber(),
                draft.getHomeGoalkeeperColorBorder(),
                draft.getAwayPlayerColorPrimary(), draft.getAwayPlayerColorNumber(),
                draft.getAwayPlayerColorBorder(),
                draft.getAwayGoalkeeperColorPrimary(), draft.getAwayGoalkeeperColorNumber(),
                draft.getAwayGoalkeeperColorBorder(),
                generationId, draft.getFixtureId(), draft.getSeason());
        if (updated != 1 && !fixtureGenerationExists(generationId, draft.getFixtureId(), draft.getSeason())) {
            throw new IllegalStateException("Fixture generation row is missing or changed season: " + draft.getFixtureId());
        }
    }

    private boolean fixtureGenerationExists(long generationId, Long fixtureId, Integer season) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_generation
                WHERE generation_id = ? AND fixture_id = ? AND season = ?
                """, Integer.class, generationId, fixtureId, season);
        return count != null && count == 1;
    }

    @Transactional
    public void applyEvents(long generationId, Long fixtureRowId, List<EventRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status) || !fixtureRowExists(generationId, fixtureRowId)) {
            throw new IllegalStateException("Fixture event generation is not writable: " + generationId);
        }
        for (EventRow row : rows) {
            jdbcTemplate.update("""
                    INSERT INTO fixture_event_generation
                      (generation_id, fixture_id, event_sequence, elapsed, extra, team_id,
                       player_id, assist_player_id, event_type, event_detail, comments)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      elapsed = VALUES(elapsed), extra = VALUES(extra),
                      team_id = VALUES(team_id), player_id = VALUES(player_id),
                      assist_player_id = VALUES(assist_player_id),
                      event_type = VALUES(event_type), event_detail = VALUES(event_detail),
                      comments = VALUES(comments)
                    """, generationId, fixtureRowId, row.sequence(), row.elapsed(), row.extra(),
                    row.teamId(), row.playerId(), row.assistPlayerId(),
                    row.type(), row.detail(), row.comments());
        }
    }

    private boolean fixtureRowExists(long generationId, Long fixtureRowId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_generation WHERE generation_id = ? AND id = ?
                """, Integer.class, generationId, fixtureRowId);
        return count != null && count == 1;
    }

    public record EventRow(int sequence, Integer elapsed, Integer extra, Long teamId,
                           Long playerId, Long assistPlayerId, String type, String detail, String comments) {
    }

    @Transactional
    public void applyTeamStats(long generationId, Long fixtureRowId, List<FixtureStat> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status) || !fixtureRowExists(generationId, fixtureRowId)) {
            throw new IllegalStateException("Fixture statistic generation is not writable: " + generationId);
        }
        for (FixtureStat stat : drafts) {
            jdbcTemplate.update("""
                    INSERT INTO fixture_stat_generation
                      (id, version, fixture_id, team_id, shots_on_goal, shots_off_goal,
                       total_shots, blocked_shots, shots_inside_box, shots_outside_box,
                       fouls, corner_kicks, offsides, ball_possession, yellow_cards,
                       red_cards, goalkeeper_saves, total_passes, passes_accurate,
                       pass_accuracy, expected_goals, generation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      shots_on_goal = VALUES(shots_on_goal), shots_off_goal = VALUES(shots_off_goal),
                      total_shots = VALUES(total_shots), blocked_shots = VALUES(blocked_shots),
                      shots_inside_box = VALUES(shots_inside_box),
                      shots_outside_box = VALUES(shots_outside_box), fouls = VALUES(fouls),
                      corner_kicks = VALUES(corner_kicks), offsides = VALUES(offsides),
                      ball_possession = VALUES(ball_possession), yellow_cards = VALUES(yellow_cards),
                      red_cards = VALUES(red_cards), goalkeeper_saves = VALUES(goalkeeper_saves),
                      total_passes = VALUES(total_passes), passes_accurate = VALUES(passes_accurate),
                      pass_accuracy = VALUES(pass_accuracy), expected_goals = VALUES(expected_goals)
                    """, stat.getId(), stat.getVersion(), fixtureRowId, stat.getTeam().getId(),
                    stat.getShotsOnGoal(), stat.getShotsOffGoal(), stat.getTotalShots(),
                    stat.getBlockedShots(), stat.getShotsInsideBox(), stat.getShotsOutsideBox(),
                    stat.getFouls(), stat.getCornerKicks(), stat.getOffsides(),
                    stat.getBallPossession(), stat.getYellowCards(), stat.getRedCards(),
                    stat.getGoalkeeperSaves(), stat.getTotalPasses(), stat.getPassesAccurate(),
                    stat.getPassAccuracy(), stat.getExpectedGoals(), generationId);
        }
    }

    @Transactional
    public void applyPlayerStats(long generationId, Long fixtureRowId, List<PlayerFixtureStat> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status) || !fixtureRowExists(generationId, fixtureRowId)) {
            throw new IllegalStateException("Fixture player generation is not writable: " + generationId);
        }
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate);
        for (PlayerFixtureStat stat : drafts) {
            jdbcTemplate.update("""
                    DELETE FROM player_fixture_stat_generation
                    WHERE generation_id = ? AND fixture_id = ? AND player_id = ?
                    """, generationId, fixtureRowId, stat.getPlayer().getId());
            MapSqlParameterSource values = new MapSqlParameterSource()
                    .addValue("id", stat.getId()).addValue("version", stat.getVersion())
                    .addValue("generationId", generationId).addValue("fixtureId", fixtureRowId)
                    .addValue("playerId", stat.getPlayer().getId()).addValue("teamId", stat.getTeam().getId())
                    .addValue("assists", stat.getAssists()).addValue("blocks", stat.getBlocks())
                    .addValue("conceded", stat.getConceded())
                    .addValue("dribblesAttempts", stat.getDribblesAttempts())
                    .addValue("dribblesPast", stat.getDribblesPast())
                    .addValue("dribblesSuccess", stat.getDribblesSuccess())
                    .addValue("duelsTotal", stat.getDuelsTotal()).addValue("duelsWon", stat.getDuelsWon())
                    .addValue("foulsCommitted", stat.getFoulsCommitted())
                    .addValue("foulsDrawn", stat.getFoulsDrawn())
                    .addValue("goals", stat.getGoals()).addValue("interceptions", stat.getInterceptions())
                    .addValue("isCaptain", stat.getIsCaptain()).addValue("isSubstitute", stat.getIsSubstitute())
                    .addValue("minutesPlayed", stat.getMinutesPlayed()).addValue("offsides", stat.getOffsides())
                    .addValue("passAccuracy", stat.getPassAccuracy())
                    .addValue("passesAccurate", stat.getPassesAccurate())
                    .addValue("passesKey", stat.getPassesKey()).addValue("passesTotal", stat.getPassesTotal())
                    .addValue("penaltyCommitted", stat.getPenaltyCommitted())
                    .addValue("penaltyMissed", stat.getPenaltyMissed())
                    .addValue("penaltySaved", stat.getPenaltySaved())
                    .addValue("penaltyScored", stat.getPenaltyScored())
                    .addValue("penaltyWon", stat.getPenaltyWon())
                    .addValue("rating", stat.getRating()).addValue("redCards", stat.getRedCards())
                    .addValue("saves", stat.getSaves()).addValue("shotsOnTarget", stat.getShotsOnTarget())
                    .addValue("shotsTotal", stat.getShotsTotal())
                    .addValue("tacklesTotal", stat.getTacklesTotal())
                    .addValue("yellowCards", stat.getYellowCards());
            named.update("""
                    INSERT INTO player_fixture_stat_generation
                      (id, version, generation_id, fixture_id, player_id, team_id,
                       assists, blocks, conceded, dribbles_attempts, dribbles_past,
                       dribbles_success, duels_total, duels_won, fouls_committed,
                       fouls_drawn, goals, interceptions, is_captain, is_substitute,
                       minutes_played, offsides, pass_accuracy, passes_accurate,
                       passes_key, passes_total, penalty_committed, penalty_missed,
                       penalty_saved, penalty_scored, penalty_won, rating, red_cards,
                       saves, shots_on_target, shots_total, tackles_total, yellow_cards)
                    VALUES
                      (:id, :version, :generationId, :fixtureId, :playerId, :teamId,
                       :assists, :blocks, :conceded, :dribblesAttempts, :dribblesPast,
                       :dribblesSuccess, :duelsTotal, :duelsWon, :foulsCommitted,
                       :foulsDrawn, :goals, :interceptions, :isCaptain, :isSubstitute,
                       :minutesPlayed, :offsides, :passAccuracy, :passesAccurate,
                       :passesKey, :passesTotal, :penaltyCommitted, :penaltyMissed,
                       :penaltySaved, :penaltyScored, :penaltyWon, :rating, :redCards,
                       :saves, :shotsOnTarget, :shotsTotal, :tacklesTotal, :yellowCards)
                    """, values);
        }
    }

    @Transactional
    public void applyLineups(long generationId, Long fixtureRowId, List<FixtureLineup> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status) || !fixtureRowExists(generationId, fixtureRowId)) {
            throw new IllegalStateException("Fixture lineup generation is not writable: " + generationId);
        }
        for (FixtureLineup lineup : drafts) {
            jdbcTemplate.update("""
                    DELETE FROM fixture_lineup_generation
                    WHERE generation_id = ? AND fixture_id = ? AND team_id = ? AND player_id = ?
                    """, generationId, fixtureRowId, lineup.getTeam().getId(), lineup.getPlayer().getId());
            jdbcTemplate.update("""
                    INSERT INTO fixture_lineup_generation
                      (id, version, generation_id, fixture_id, team_id, player_id,
                       jersey_number, position, grid, is_starter)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, lineup.getId(), lineup.getVersion(), generationId, fixtureRowId,
                    lineup.getTeam().getId(), lineup.getPlayer().getId(), lineup.getJerseyNumber(),
                    lineup.getPosition(), lineup.getGrid(), lineup.isStarter());
        }
    }

    @Transactional
    public void markFixtureMaterialized(long generationId, Long fixtureId) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status)) {
            throw new IllegalStateException("Generation is no longer building: " + generationId);
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO fixture_detail_generation_materialized (generation_id, fixture_id)
                SELECT r.generation_id, r.fixture_id
                FROM fixture_detail_generation_response r
                JOIN fixture_generation f
                  ON f.generation_id = r.generation_id AND f.fixture_id = r.fixture_id
                WHERE r.generation_id = ? AND r.fixture_id = ?
                """, generationId, fixtureId);
        if (inserted != 1) {
            throw new IllegalStateException("Fixture generation response has not been stored: " + fixtureId);
        }
    }

    @Transactional
    public void markReady(long generationId) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM fixture_detail_generation WHERE id = ? FOR UPDATE",
                String.class, generationId);
        if (!"BUILDING".equals(status)) {
            throw new IllegalStateException("Only a building generation can become ready: " + generationId);
        }
        Integer missingResponses = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_generation_target t
                LEFT JOIN fixture_detail_generation_response r
                  ON r.generation_id = t.generation_id AND r.fixture_id = t.fixture_id
                WHERE t.generation_id = ? AND r.fixture_id IS NULL
                """, Integer.class, generationId);
        Integer missingMaterialized = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_generation_target t
                LEFT JOIN fixture_detail_generation_materialized m
                  ON m.generation_id = t.generation_id AND m.fixture_id = t.fixture_id
                WHERE t.generation_id = ? AND m.fixture_id IS NULL
                """, Integer.class, generationId);
        Integer missing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_generation_target t
                LEFT JOIN fixture_generation f
                  ON f.generation_id = t.generation_id AND f.fixture_id = t.fixture_id
                WHERE t.generation_id = ? AND f.id IS NULL
                """, Integer.class, generationId);
        Integer unexpected = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_generation f
                LEFT JOIN fixture_detail_generation_target t
                  ON t.generation_id = f.generation_id AND t.fixture_id = f.fixture_id
                WHERE f.generation_id = ? AND t.fixture_id IS NULL
                """, Integer.class, generationId);
        Integer wrongSeason = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_generation f
                JOIN fixture_detail_generation g ON g.id = f.generation_id
                WHERE f.generation_id = ? AND (f.season IS NULL OR f.season <> g.season)
                """, Integer.class, generationId);
        Integer wrongAggregateSeason = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM player_team_season_stat_generation s
                JOIN fixture_detail_generation g ON g.id = s.generation_id
                WHERE s.generation_id = ? AND s.season <> g.season
                """, Integer.class, generationId);
        if (missingResponses == null || missingResponses != 0
                || missingMaterialized == null || missingMaterialized != 0
                || missing == null || missing != 0 || unexpected == null || unexpected != 0
                || wrongSeason == null || wrongSeason != 0
                || wrongAggregateSeason == null || wrongAggregateSeason != 0) {
            throw new IllegalStateException("Generation fixture targets or seasons are inconsistent: " + generationId);
        }
        int updated = jdbcTemplate.update("""
                UPDATE fixture_detail_generation
                SET status = 'READY'
                WHERE id = ? AND status = 'BUILDING'
                  AND normalized_at IS NOT NULL AND aggregated_at IS NOT NULL
                  AND merged_at IS NOT NULL
                  AND expected_fixture_count = (
                    SELECT COUNT(*) FROM fixture_detail_generation_target WHERE generation_id = ?)
                """, generationId, generationId);
        if (updated != 1) {
            throw new IllegalStateException("Generation is not complete or no longer building: " + generationId);
        }
    }

    @Transactional
    public void markNormalized(long generationId) {
        int updated = jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET normalized_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'BUILDING'
                  AND expected_fixture_count = (
                    SELECT COUNT(*) FROM fixture_generation WHERE generation_id = ?)
                  AND expected_fixture_count = (
                    SELECT COUNT(*) FROM fixture_detail_generation_materialized WHERE generation_id = ?)
                """, generationId, generationId, generationId);
        if (updated != 1) {
            throw new IllegalStateException("Generation fixtures have not all been materialized: " + generationId);
        }
    }

    @Transactional
    public void markAggregated(long generationId) {
        Integer missingAggregates = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT player_id, team_id FROM player_fixture_stat_generation
                    WHERE generation_id = ?
                    UNION
                    SELECT player_id, team_id FROM fixture_lineup_generation
                    WHERE generation_id = ?
                ) fixture_keys
                LEFT JOIN fixture_detail_generation g ON g.id = ?
                LEFT JOIN player_team_season_stat_generation s
                  ON s.generation_id = g.id AND s.season = g.season AND s.league_id = 39
                  AND s.player_id = fixture_keys.player_id AND s.team_id = fixture_keys.team_id
                WHERE s.id IS NULL
                """, Integer.class, generationId, generationId, generationId);
        if (missingAggregates == null || missingAggregates != 0) {
            throw new IllegalStateException("Generation player aggregates are incomplete: " + generationId);
        }
        int updated = jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET aggregated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'BUILDING' AND normalized_at IS NOT NULL
                """, generationId);
        if (updated != 1) {
            throw new IllegalStateException("Generation has not been normalized: " + generationId);
        }
    }

    @Transactional
    public void markMerged(long generationId) {
        int updated = jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET merged_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'BUILDING'
                  AND normalized_at IS NOT NULL AND aggregated_at IS NOT NULL
                """, generationId);
        if (updated != 1) {
            throw new IllegalStateException("Generation cannot be marked merged: " + generationId);
        }
    }

    @Transactional
    public void publish(int season, long generationId) {
        List<Long> activeIds = jdbcTemplate.queryForList(
                "SELECT generation_id FROM fixture_detail_active_generation WHERE season = ? FOR UPDATE",
                Long.class, season);
        if (activeIds.size() > 1) {
            throw new IllegalStateException("Season has multiple active fixture detail generations: " + season);
        }
        Long previousId = activeIds.isEmpty() ? null : activeIds.get(0);
        if (previousId != null && previousId == generationId) {
            return; // A Redis Stream delivery can be repeated after DB commit but before ACK.
        }
        if (previousId != null && previousId > generationId) {
            throw new IllegalStateException("Refusing to publish an older fixture detail generation: " + generationId);
        }
        int ready = jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET status = 'ACTIVE', published_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND season = ? AND status = 'READY'
                """, generationId, season);
        if (ready != 1) {
            throw new IllegalStateException("Fixture detail generation is not ready: " + generationId);
        }
        if (previousId == null) {
            jdbcTemplate.update("""
                    INSERT INTO fixture_detail_active_generation (season, generation_id)
                    VALUES (?, ?)
                    """, season, generationId);
        } else {
            jdbcTemplate.update("UPDATE fixture_detail_active_generation SET generation_id = ? WHERE season = ?",
                    generationId, season);
            jdbcTemplate.update("UPDATE fixture_detail_generation SET status = 'RETIRED' WHERE id = ?", previousId);
        }
    }

    @Transactional
    public void markFailed(long generationId) {
        jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET status = 'FAILED'
                WHERE id = ? AND status IN ('BUILDING', 'READY')
                """, generationId);
    }

    @Transactional(readOnly = true)
    public List<Integer> activeSeasons() {
        return jdbcTemplate.queryForList(
                "SELECT season FROM fixture_detail_active_generation ORDER BY season DESC", Integer.class);
    }

    @Transactional(readOnly = true)
    public Integer publishedJobFixtureCount(String jobId) {
        if (jobId == null) {
            return null;
        }
        List<Integer> counts = jdbcTemplate.queryForList("""
                SELECT fixture_count FROM fixture_detail_published_job WHERE job_id = ?
                """, Integer.class, jobId);
        return counts.isEmpty() ? null : counts.get(0);
    }

    @Transactional(readOnly = true)
    public Long activeLiveRevision(int season) {
        List<Long> revisions = jdbcTemplate.queryForList("""
                SELECT generation.live_revision FROM fixture_detail_active_generation active
                JOIN fixture_detail_generation generation ON generation.id = active.generation_id
                WHERE active.season = ?
                """, Long.class, season);
        return revisions.isEmpty() ? null : revisions.get(0);
    }

    @Transactional(readOnly = true)
    public Long activeGenerationId(int season) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT generation_id FROM fixture_detail_active_generation WHERE season = ?",
                Long.class, season);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
