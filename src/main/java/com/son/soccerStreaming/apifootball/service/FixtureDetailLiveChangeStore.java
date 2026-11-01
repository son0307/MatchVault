package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FixtureDetailLiveChangeStore {

    public enum Part {
        FIXTURE("fixture_changes"), EVENT("event_changes"), LINEUP("lineup_changes"),
        TEAM_STAT("team_stat_changes"), PLAYER_STAT("player_stat_changes");

        private final String column;

        Part(String column) {
            this.column = column;
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public boolean changed(int season, long fixtureId, Part part) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM fixture_detail_season_lock season_lock,
                  JSON_TABLE(season_lock.%s, '$[*]'
                    COLUMNS (fixture_id BIGINT PATH '$')) changed
                WHERE season_lock.season = ? AND changed.fixture_id = ?
                """.formatted(part.column), Integer.class, season, fixtureId);
        return count != null && count > 0;
    }
}
