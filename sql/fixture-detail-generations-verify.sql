-- Run immediately after fixture-detail-generations.sql while detail writers are paused.
-- Every row below should have status ACTIVE and all *_difference columns equal to 0.
SELECT g.season, g.id AS generation_id, g.status,
       g.expected_fixture_count -
           (SELECT COUNT(*) FROM fixture_generation v WHERE v.generation_id = g.id)
           AS expected_fixture_difference,
       (SELECT COUNT(*) FROM fixture f WHERE f.season = g.season) -
           (SELECT COUNT(*) FROM fixture_generation v WHERE v.generation_id = g.id)
           AS fixture_difference,
       (SELECT COUNT(*) FROM fixture_event e JOIN fixture f ON f.id = e.fixture_id
        WHERE f.season = g.season) -
           (SELECT COUNT(*) FROM fixture_event_generation v WHERE v.generation_id = g.id)
           AS event_difference,
       (SELECT COUNT(*) FROM fixture_lineup e JOIN fixture f ON f.id = e.fixture_id
        WHERE f.season = g.season) -
           (SELECT COUNT(*) FROM fixture_lineup_generation v WHERE v.generation_id = g.id)
           AS lineup_difference,
       (SELECT COUNT(*) FROM fixture_stat e JOIN fixture f ON f.id = e.fixture_id
        WHERE f.season = g.season) -
           (SELECT COUNT(*) FROM fixture_stat_generation v WHERE v.generation_id = g.id)
           AS team_stat_difference,
       (SELECT COUNT(*) FROM player_fixture_stat e JOIN fixture f ON f.id = e.fixture_id
        WHERE f.season = g.season) -
           (SELECT COUNT(*) FROM player_fixture_stat_generation v WHERE v.generation_id = g.id)
           AS player_stat_difference,
       (SELECT COUNT(*) FROM player_team_season_stat e WHERE e.season = g.season) -
           (SELECT COUNT(*) FROM player_team_season_stat_generation v WHERE v.generation_id = g.id)
           AS player_season_stat_difference
FROM fixture_detail_generation g
JOIN fixture_detail_active_generation a ON a.season = g.season AND a.generation_id = g.id
ORDER BY g.season;

-- No row should be returned: each source season must have an active pointer.
SELECT source_seasons.season
FROM (SELECT season FROM fixture WHERE season IS NOT NULL
      UNION SELECT season FROM player_team_season_stat) source_seasons
LEFT JOIN fixture_detail_active_generation a ON a.season = source_seasons.season
WHERE a.season IS NULL;

-- No row should be returned: the active pointer must lead to an ACTIVE generation.
SELECT a.season, a.generation_id, g.status
FROM fixture_detail_active_generation a
JOIN fixture_detail_generation g ON g.id = a.generation_id
WHERE g.status <> 'ACTIVE' OR g.season <> a.season;
