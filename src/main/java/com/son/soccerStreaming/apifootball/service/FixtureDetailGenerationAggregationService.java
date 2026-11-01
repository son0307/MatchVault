package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Rebuilds player season totals from one private fixture detail generation. */
@Service
@RequiredArgsConstructor
public class FixtureDetailGenerationAggregationService {

    private static final List<String> SUM_COLUMNS = List.of(
            "shots_total", "shots_on_target", "goals", "conceded", "assists", "saves",
            "passes_total", "passes_key", "tackles_total", "blocks", "interceptions",
            "duels_total", "duels_won", "dribbles_attempts", "dribbles_success", "dribbles_past",
            "fouls_drawn", "fouls_committed", "red_cards", "penalty_won", "penalty_committed",
            "penalty_scored", "penalty_missed", "penalty_saved"
    );

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int rebuild(long generationId, int season) {
        String status = jdbcTemplate.queryForObject("""
                SELECT status FROM fixture_detail_generation
                WHERE id = ? AND season = ? AND normalized_at IS NOT NULL FOR UPDATE
                """, String.class, generationId, season);
        if (!"BUILDING".equals(status) && !"ACTIVE".equals(status)) {
            throw new IllegalStateException("Generation must be normalized or active before player aggregation: " + generationId);
        }

        jdbcTemplate.update("DELETE FROM player_team_season_stat_generation WHERE generation_id = ?", generationId);
        // Retain profile/API season rows that are not derived from fixture details.
        jdbcTemplate.update("""
                INSERT INTO player_team_season_stat_generation
                SELECT current_stat.*, ? FROM player_team_season_stat current_stat
                WHERE current_stat.season = ?
                """, generationId, season);
        jdbcTemplate.update("""
                DELETE season_stat FROM player_team_season_stat_generation season_stat
                JOIN (
                    SELECT player_id, team_id FROM player_fixture_stat_generation
                    WHERE generation_id = ?
                    UNION
                    SELECT player_id, team_id FROM fixture_lineup_generation
                    WHERE generation_id = ?
                ) fixture_keys ON fixture_keys.player_id = season_stat.player_id
                    AND fixture_keys.team_id = season_stat.team_id
                WHERE season_stat.generation_id = ? AND season_stat.league_id = 39
                """, generationId, generationId, generationId);

        String sumExpressions = SUM_COLUMNS.stream()
                .map(column -> "SUM(COALESCE(s." + column + ", 0)) AS " + column)
                .collect(Collectors.joining(",\n                           "));
        String resultExpressions = SUM_COLUMNS.stream()
                .map(column -> "COALESCE(appearance." + column + ", 0)")
                .collect(Collectors.joining(",\n                       "));
        String columns = String.join(", ", SUM_COLUMNS);
        String sql = """
                INSERT INTO player_team_season_stat_generation
                  (generation_id, league_id, season, player_id, team_id,
                   back_number, position, appearances, lineups, minutes, rating, captain,
                   substitutes_in, substitutes_out, substitutes_bench, pass_accuracy,
                   yellow_cards, yellow_red_cards, %s)
                SELECT :generationId, 39, generation.season, fixture_keys.player_id, fixture_keys.team_id,
                   (SELECT lineup.jersey_number FROM fixture_lineup_generation lineup
                    JOIN fixture_generation fixture
                      ON fixture.id = lineup.fixture_id AND fixture.generation_id = lineup.generation_id
                    WHERE lineup.generation_id = :generationId
                      AND lineup.player_id = fixture_keys.player_id
                      AND lineup.team_id = fixture_keys.team_id
                    ORDER BY fixture.fixture_date DESC, fixture.fixture_id DESC LIMIT 1),
                   COALESCE(
                     (SELECT lineup.position FROM fixture_lineup_generation lineup
                      JOIN fixture_generation fixture
                        ON fixture.id = lineup.fixture_id AND fixture.generation_id = lineup.generation_id
                      WHERE lineup.generation_id = :generationId
                        AND lineup.player_id = fixture_keys.player_id
                        AND lineup.team_id = fixture_keys.team_id
                      ORDER BY fixture.fixture_date DESC, fixture.fixture_id DESC LIMIT 1),
                     player.position),
                   COALESCE(appearance.appearances, 0), COALESCE(appearance.lineups, 0),
                   COALESCE(appearance.minutes, 0), appearance.rating,
                   COALESCE(appearance.captain, 0), COALESCE(appearance.substitutes_in, 0),
                   NULL, COALESCE(bench.substitutes_bench, 0),
                   CASE WHEN appearance.passes_total > 0
                     THEN ROUND(appearance.passes_accurate * 100.0 / appearance.passes_total)
                     ELSE NULL END,
                   COALESCE(appearance.yellow_cards, 0), NULL, %s
                FROM (
                    SELECT player_id, team_id FROM player_fixture_stat_generation
                    WHERE generation_id = :generationId
                    UNION
                    SELECT player_id, team_id FROM fixture_lineup_generation
                    WHERE generation_id = :generationId
                ) fixture_keys
                JOIN fixture_detail_generation generation ON generation.id = :generationId
                JOIN player player ON player.id = fixture_keys.player_id
                LEFT JOIN (
                    SELECT s.player_id, s.team_id, COUNT(*) AS appearances,
                           SUM(CASE WHEN s.is_substitute = b'1' THEN 0 ELSE 1 END) AS lineups,
                           SUM(s.minutes_played) AS minutes,
                           ROUND(AVG(s.rating), 2) AS rating,
                           MAX(CASE WHEN s.is_captain = b'1' THEN 1 ELSE 0 END) AS captain,
                           SUM(CASE WHEN s.is_substitute = b'1' THEN 1 ELSE 0 END) AS substitutes_in,
                           SUM(COALESCE(s.passes_accurate, 0)) AS passes_accurate,
                           SUM(CASE WHEN COALESCE(s.red_cards, 0) > 0 THEN 0
                                    ELSE COALESCE(s.yellow_cards, 0) END) AS yellow_cards,
                           %s
                    FROM player_fixture_stat_generation s
                    WHERE s.generation_id = :generationId AND COALESCE(s.minutes_played, 0) > 0
                    GROUP BY s.player_id, s.team_id
                ) appearance ON appearance.player_id = fixture_keys.player_id
                    AND appearance.team_id = fixture_keys.team_id
                LEFT JOIN (
                    SELECT lineup.player_id, lineup.team_id, COUNT(*) AS substitutes_bench
                    FROM fixture_lineup_generation lineup
                    LEFT JOIN player_fixture_stat_generation stat
                      ON stat.generation_id = lineup.generation_id
                      AND stat.fixture_id = lineup.fixture_id
                      AND stat.player_id = lineup.player_id
                      AND stat.team_id = lineup.team_id
                      AND COALESCE(stat.minutes_played, 0) > 0
                    WHERE lineup.generation_id = :generationId AND stat.id IS NULL
                    GROUP BY lineup.player_id, lineup.team_id
                ) bench ON bench.player_id = fixture_keys.player_id
                    AND bench.team_id = fixture_keys.team_id
                """.formatted(columns, resultExpressions, sumExpressions);
        return new NamedParameterJdbcTemplate(jdbcTemplate).update(
                sql, new MapSqlParameterSource("generationId", generationId));
    }
}
