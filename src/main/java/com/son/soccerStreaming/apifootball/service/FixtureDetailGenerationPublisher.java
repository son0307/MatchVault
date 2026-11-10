package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.global.config.RankingCacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reconciles committed base-table writes before switching the season pointer. */
@Service
@RequiredArgsConstructor
public class FixtureDetailGenerationPublisher {

    private static final Map<String, String> CHANGED_TABLES = Map.of(
            "event_changes", "fixture_event",
            "lineup_changes", "fixture_lineup",
            "team_stat_changes", "fixture_stat",
            "player_stat_changes", "player_fixture_stat"
    );
    private static final Map<String, String> PROJECTED_COUNTS = Map.of(
            "fixture_changes", "projected_fixture_count",
            "event_changes", "projected_event_count",
            "lineup_changes", "projected_lineup_count",
            "team_stat_changes", "projected_team_stat_count",
            "player_stat_changes", "projected_player_stat_count"
    );

    private final JdbcTemplate jdbcTemplate;
    private final FixtureDetailGenerationStore generationStore;
    private final FixtureDetailGenerationAggregationService aggregationService;
    private final FixtureDetailFixtureFieldStore fixtureFieldStore;
    private final RankingCacheInvalidationService rankingCacheInvalidationService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publish(int season, long generationId) {
        publish(season, generationId, null, 0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publish(int season, long generationId, String jobId, int fixtureCount) {
        // Base-table triggers acquire this row in their own transaction. A writer that
        // arrives after this lock is reflected as a later live change, not lost here.
        List<Integer> locks = jdbcTemplate.queryForList(
                "SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, season);
        if (locks.size() != 1) {
            throw new IllegalStateException("Season mutation lock is missing: " + season);
        }
        Long activeGenerationId = generationStore.activeGenerationId(season);
        if (activeGenerationId != null && activeGenerationId == generationId) {
            return; // The DB commit succeeded before the Redis message was acknowledged.
        }
        if (activeGenerationId != null && activeGenerationId > generationId) {
            throw new IllegalStateException("Refusing to replay an older generation: " + generationId);
        }
        long startRevision = jdbcTemplate.queryForObject("""
                SELECT start_fixture_revision FROM fixture_detail_generation WHERE id = ? AND season = ?
                """, Long.class, generationId, season);
        List<Long> fixtureChanges = jdbcTemplate.queryForList("""
                SELECT fixture_id FROM (
                  SELECT changed.fixture_id FROM fixture_detail_season_lock season_lock,
                    JSON_TABLE(season_lock.fixture_changes, '$[*]'
                      COLUMNS (fixture_id BIGINT PATH '$')) changed
                  WHERE season_lock.season = ?
                  UNION
                  SELECT admin_change.target_id FROM admin_field_override admin_change
                  JOIN fixture f ON f.fixture_id = admin_change.target_id
                  JOIN fixture_detail_generation_target target
                    ON target.fixture_id = f.fixture_id AND target.generation_id = ?
                  WHERE admin_change.target_type = 'FIXTURE' AND f.season = ?
                ) changes
                """, Long.class, season, generationId, season);
        for (Long fixtureId : fixtureChanges) {
            requireTarget(generationId, fixtureId);
            copyFixture(generationId, fixtureId, startRevision);
        }
        boolean aggregateChanged = false;
        for (Map.Entry<String, String> entry : CHANGED_TABLES.entrySet()) {
            for (Long fixtureId : changedFixtureIds(entry.getKey(), season)) {
                requireTarget(generationId, fixtureId);
                copyChildren(generationId, fixtureId, entry.getValue());
                aggregateChanged |= entry.getKey().equals("lineup_changes")
                        || entry.getKey().equals("player_stat_changes");
            }
        }
        if (aggregateChanged) {
            aggregationService.rebuild(generationId, season);
            generationStore.markAggregated(generationId);
        }
        generationStore.markMerged(generationId);
        generationStore.markReady(generationId);
        generationStore.publish(season, generationId);
        if (jobId != null) {
            jdbcTemplate.update("""
                    INSERT INTO fixture_detail_published_job
                      (job_id, season, generation_id, fixture_count)
                    VALUES (?, ?, ?, ?)
                    """, jobId, season, generationId, fixtureCount);
        }
        jdbcTemplate.update("""
                UPDATE fixture_detail_season_lock SET
                  fixture_changes = JSON_ARRAY(), event_changes = JSON_ARRAY(),
                  lineup_changes = JSON_ARRAY(), team_stat_changes = JSON_ARRAY(),
                  player_stat_changes = JSON_ARRAY(),
                  projected_fixture_count = 0, projected_event_count = 0,
                  projected_lineup_count = 0, projected_team_stat_count = 0,
                  projected_player_stat_count = 0,
                  projected_fixture_revision = fixture_revision
                WHERE season = ?
                """, season);
        rankingCacheInvalidationService.invalidateAfterCommit(season);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean projectLiveChanges(int season) {
        List<Integer> locks = jdbcTemplate.queryForList(
                "SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, season);
        if (locks.isEmpty()) {
            return false;
        }
        Long activeGenerationId = generationStore.activeGenerationId(season);
        if (activeGenerationId == null) {
            return false;
        }
        long projectedRevision = jdbcTemplate.queryForObject("""
                SELECT projected_fixture_revision FROM fixture_detail_season_lock WHERE season = ?
                """, Long.class, season);
        List<Long> fixtureChanges = unprojectedFixtureIds("fixture_changes", season);
        for (Long fixtureId : fixtureChanges) {
            copyFixture(activeGenerationId, fixtureId, projectedRevision);
        }
        boolean changed = !fixtureChanges.isEmpty();
        boolean aggregateChanged = false;
        for (Map.Entry<String, String> entry : CHANGED_TABLES.entrySet()) {
            List<Long> fixtureIds = unprojectedFixtureIds(entry.getKey(), season);
            for (Long fixtureId : fixtureIds) {
                copyChildren(activeGenerationId, fixtureId, entry.getValue());
            }
            changed |= !fixtureIds.isEmpty();
            if (entry.getKey().equals("lineup_changes") || entry.getKey().equals("player_stat_changes")) {
                aggregateChanged |= !fixtureIds.isEmpty();
            }
        }
        if (!changed) {
            return false;
        }
        if (aggregateChanged) {
            aggregationService.rebuild(activeGenerationId, season);
        }
        jdbcTemplate.update("""
                UPDATE fixture_detail_generation SET live_revision = live_revision + 1
                WHERE id = ? AND season = ? AND status = 'ACTIVE'
                """, activeGenerationId, season);
        jdbcTemplate.update("""
                UPDATE fixture_detail_season_lock SET
                  projected_fixture_count = JSON_LENGTH(fixture_changes),
                  projected_fixture_revision = fixture_revision,
                  projected_event_count = JSON_LENGTH(event_changes),
                  projected_lineup_count = JSON_LENGTH(lineup_changes),
                  projected_team_stat_count = JSON_LENGTH(team_stat_changes),
                  projected_player_stat_count = JSON_LENGTH(player_stat_changes)
                WHERE season = ?
                """, season);
        rankingCacheInvalidationService.invalidateAfterCommit(season);
        return true;
    }

    private List<Long> unprojectedFixtureIds(String column, int season) {
        String countColumn = PROJECTED_COUNTS.get(column);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT changed.fixture_id
                FROM fixture_detail_season_lock season_lock,
                  JSON_TABLE(season_lock.%s, '$[*]'
                    COLUMNS (ordinal FOR ORDINALITY, fixture_id BIGINT PATH '$')) changed
                WHERE season_lock.season = ? AND changed.ordinal > season_lock.%s
                """.formatted(column, countColumn), Long.class, season);
    }

    private List<Long> changedFixtureIds(String column, int season) {
        // The column is a constant from this class, never user input.
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT changed.fixture_id
                FROM fixture_detail_season_lock season_lock,
                  JSON_TABLE(season_lock.%s, '$[*]'
                    COLUMNS (fixture_id BIGINT PATH '$')) changed
                WHERE season_lock.season = ?
                """.formatted(column), Long.class, season);
    }

    private void requireTarget(long generationId, long fixtureId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_generation_target
                WHERE generation_id = ? AND fixture_id = ?
                """, Integer.class, generationId, fixtureId);
        if (count == null || count != 1) {
            throw new IllegalStateException("A fixture changed outside the generation target set: " + fixtureId);
        }
    }

    private void copyFixture(long generationId, long fixtureId, long afterRevision) {
        Map<String, Object> live = jdbcTemplate.queryForMap(
                "SELECT * FROM fixture WHERE fixture_id = ?", fixtureId);
        Integer existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_generation WHERE generation_id = ? AND id = ?
                """, Integer.class, generationId, live.get("id"));
        if (existing == null || existing == 0) {
            String columns = live.keySet().stream().map(this::quoted).collect(Collectors.joining(", "));
            String placeholders = live.keySet().stream().map(ignored -> "?").collect(Collectors.joining(", "));
            List<Object> values = new ArrayList<>(live.values());
            values.add(generationId);
            jdbcTemplate.update("INSERT INTO fixture_generation (" + columns
                    + ", generation_id) VALUES (" + placeholders + ", ?)", values.toArray());
            return;
        }
        fixtureFieldStore.mergeIntoGeneration(generationId, fixtureId, afterRevision);
    }

    private void copyChildren(long generationId, long fixtureId, String table) {
        Long fixtureRowId = jdbcTemplate.queryForObject(
                "SELECT id FROM fixture WHERE fixture_id = ?", Long.class, fixtureId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + table + " WHERE fixture_id = ?", fixtureRowId);
        jdbcTemplate.update("DELETE FROM " + table + "_generation WHERE generation_id = ? AND fixture_id = ?",
                generationId, fixtureRowId);
        for (Map<String, Object> row : rows) {
            String columns = row.keySet().stream().map(this::quoted).collect(Collectors.joining(", "));
            String placeholders = row.keySet().stream().map(ignored -> "?").collect(Collectors.joining(", "));
            List<Object> values = new ArrayList<>(row.values());
            values.add(generationId);
            jdbcTemplate.update("INSERT INTO " + table + "_generation (" + columns
                    + ", generation_id) VALUES (" + placeholders + ", ?)", values.toArray());
        }
    }

    private String quoted(String column) {
        if (!column.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Unexpected database column name");
        }
        return "`" + column + "`";
    }
}
