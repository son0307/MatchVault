package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureDetailGenerationCleanupService {

    private static final String ELIGIBLE = """
            NOT EXISTS (
                SELECT 1 FROM fixture_detail_active_generation active
                WHERE active.generation_id = g.id
            )
            AND (
                (g.status = 'FAILED' AND g.created_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ? DAY))
                OR (g.status = 'RETIRED'
                    AND (SELECT COUNT(*) FROM fixture_detail_generation newer_retired
                         WHERE newer_retired.season = g.season
                           AND newer_retired.status = 'RETIRED' AND newer_retired.id > g.id) >= ?
                    AND EXISTS (
                        SELECT 1 FROM fixture_detail_generation successor
                        WHERE successor.season = g.season AND successor.id > g.id
                          AND successor.status IN ('ACTIVE', 'RETIRED')
                          AND successor.published_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ? DAY)
                    ))
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<Candidate> findCandidates(int retentionDays, int retiredToKeep, int limit) {
        return jdbcTemplate.query("SELECT g.id, g.season FROM fixture_detail_generation g WHERE "
                        + ELIGIBLE + " ORDER BY g.id LIMIT ?",
                (result, rowNumber) -> new Candidate(result.getLong("id"), result.getInt("season")),
                retentionDays, retiredToKeep, retentionDays, limit);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean deleteIfEligible(Candidate candidate, int retentionDays, int retiredToKeep) {
        // Publication and generation creation acquire this same season lock first.
        List<Integer> seasonLocks = jdbcTemplate.queryForList(
                "SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, candidate.season());
        if (seasonLocks.size() != 1) {
            return false;
        }
        Integer eligible = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fixture_detail_generation g WHERE g.id = ? AND g.season = ? AND " + ELIGIBLE,
                Integer.class, candidate.id(), candidate.season(), retentionDays, retiredToKeep, retentionDays);
        if (eligible == null || eligible != 1) {
            return false;
        }

        long generationId = candidate.id();
        jdbcTemplate.update("DELETE FROM fixture_detail_generation_response WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_detail_generation_materialized WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_detail_generation_target WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_event_generation WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_lineup_generation WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_stat_generation WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM player_fixture_stat_generation WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM fixture_generation WHERE generation_id = ?", generationId);
        jdbcTemplate.update("DELETE FROM player_team_season_stat_generation WHERE generation_id = ?", generationId);
        // Published job receipts remain so a delayed Redis delivery cannot replay an old job.
        int deleted = jdbcTemplate.update("DELETE FROM fixture_detail_generation WHERE id = ?", generationId);
        if (deleted != 1) {
            throw new IllegalStateException("Generation disappeared during cleanup: " + generationId);
        }
        return true;
    }

    public record Candidate(long id, int season) {
    }
}
