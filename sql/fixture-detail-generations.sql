-- MySQL 8 / InnoDB. Run once after a backup, before enabling generation-aware reads.
-- This creates a complete generation 1 from the current published tables.
-- Existing tables remain in place for the current application version.

CREATE TABLE fixture_detail_generation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expected_fixture_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    normalized_at DATETIME(6) DEFAULT NULL,
    aggregated_at DATETIME(6) DEFAULT NULL,
    merged_at DATETIME(6) DEFAULT NULL,
    published_at DATETIME(6) DEFAULT NULL,
    live_revision BIGINT NOT NULL DEFAULT 0,
    start_fixture_revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixture_detail_generation_season_id (season, id),
    KEY idx_fixture_detail_generation_season_status (season, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_detail_active_generation (
    season INT NOT NULL,
    generation_id BIGINT NOT NULL,
    PRIMARY KEY (season),
    CONSTRAINT fk_fixture_detail_active_generation
        FOREIGN KEY (season, generation_id)
        REFERENCES fixture_detail_generation (season, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_detail_published_job (
    job_id VARCHAR(512) NOT NULL,
    season INT NOT NULL,
    generation_id BIGINT NOT NULL,
    fixture_count INT NOT NULL,
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (job_id),
    CONSTRAINT fk_fixture_detail_published_job_generation
        FOREIGN KEY (season, generation_id)
        REFERENCES fixture_detail_generation (season, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_detail_season_lock (
    season INT NOT NULL,
    fixture_changes JSON NOT NULL DEFAULT (JSON_ARRAY()),
    event_changes JSON NOT NULL DEFAULT (JSON_ARRAY()),
    lineup_changes JSON NOT NULL DEFAULT (JSON_ARRAY()),
    team_stat_changes JSON NOT NULL DEFAULT (JSON_ARRAY()),
    player_stat_changes JSON NOT NULL DEFAULT (JSON_ARRAY()),
    projected_fixture_count INT NOT NULL DEFAULT 0,
    fixture_revision BIGINT NOT NULL DEFAULT 0,
    projected_fixture_revision BIGINT NOT NULL DEFAULT 0,
    projected_event_count INT NOT NULL DEFAULT 0,
    projected_lineup_count INT NOT NULL DEFAULT 0,
    projected_team_stat_count INT NOT NULL DEFAULT 0,
    projected_player_stat_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The value stays in fixture. This records which columns changed after a generation
-- snapshot. Keeping the latest revision per column avoids replaying older API data.
CREATE TABLE fixture_detail_fixture_field_change (
    season INT NOT NULL,
    fixture_id BIGINT NOT NULL,
    field_name VARCHAR(80) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (season, fixture_id, field_name),
    KEY idx_fixture_detail_fixture_field_revision (season, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_detail_generation_target (
    generation_id BIGINT NOT NULL,
    fixture_id BIGINT NOT NULL,
    PRIMARY KEY (generation_id, fixture_id),
    CONSTRAINT fk_fixture_detail_generation_target_generation
        FOREIGN KEY (generation_id) REFERENCES fixture_detail_generation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Retain the validated API response until it has been materialized into the six version tables.
-- A retry can replace only its own generation's response without affecting published rows.
CREATE TABLE fixture_detail_generation_response (
    generation_id BIGINT NOT NULL,
    fixture_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (generation_id, fixture_id),
    CONSTRAINT fk_fixture_detail_generation_response_target
        FOREIGN KEY (generation_id, fixture_id)
        REFERENCES fixture_detail_generation_target (generation_id, fixture_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Written only after the staged response has replaced the copied baseline in all detail tables.
CREATE TABLE fixture_detail_generation_materialized (
    generation_id BIGINT NOT NULL,
    fixture_id BIGINT NOT NULL,
    applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (generation_id, fixture_id),
    CONSTRAINT fk_fixture_detail_generation_materialized_target
        FOREIGN KEY (generation_id, fixture_id)
        REFERENCES fixture_detail_generation_target (generation_id, fixture_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_generation LIKE fixture;
ALTER TABLE fixture_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UKln3yo17vwa3y32fislcqlx510,
    ADD UNIQUE KEY uk_fixture_generation_fixture (generation_id, fixture_id),
    ADD KEY idx_fixture_generation_season_date (generation_id, season, fixture_date),
    ADD CONSTRAINT fk_fixture_generation_generation
        FOREIGN KEY (generation_id) REFERENCES fixture_detail_generation (id),
    ADD CONSTRAINT fk_fixture_generation_home_team
        FOREIGN KEY (home_team_id) REFERENCES team (id),
    ADD CONSTRAINT fk_fixture_generation_away_team
        FOREIGN KEY (away_team_id) REFERENCES team (id);

CREATE TABLE fixture_event_generation LIKE fixture_event;
ALTER TABLE fixture_event_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UKt7y6s6f76p0v9janup7i34ptl,
    ADD UNIQUE KEY uk_fixture_event_generation_sequence (generation_id, fixture_id, event_sequence),
    ADD CONSTRAINT fk_fixture_event_generation_fixture
        FOREIGN KEY (fixture_id, generation_id) REFERENCES fixture_generation (id, generation_id);

CREATE TABLE fixture_lineup_generation LIKE fixture_lineup;
ALTER TABLE fixture_lineup_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UK4jthokhjqite66ctpdm89fkbl,
    ADD UNIQUE KEY uk_fixture_lineup_generation_player (generation_id, fixture_id, team_id, player_id),
    ADD CONSTRAINT fk_fixture_lineup_generation_fixture
        FOREIGN KEY (fixture_id, generation_id) REFERENCES fixture_generation (id, generation_id);

CREATE TABLE fixture_stat_generation LIKE fixture_stat;
ALTER TABLE fixture_stat_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UKtfbd3qeerduaejpyah6cjujkf,
    ADD UNIQUE KEY uk_fixture_stat_generation_team (generation_id, fixture_id, team_id),
    ADD CONSTRAINT fk_fixture_stat_generation_fixture
        FOREIGN KEY (fixture_id, generation_id) REFERENCES fixture_generation (id, generation_id);

CREATE TABLE player_fixture_stat_generation LIKE player_fixture_stat;
ALTER TABLE player_fixture_stat_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UK7kikfer644vatjdnaa01qc9wh,
    ADD UNIQUE KEY uk_player_fixture_stat_generation_player (generation_id, fixture_id, player_id),
    ADD CONSTRAINT fk_player_fixture_stat_generation_fixture
        FOREIGN KEY (fixture_id, generation_id) REFERENCES fixture_generation (id, generation_id);

CREATE TABLE player_team_season_stat_generation LIKE player_team_season_stat;
ALTER TABLE player_team_season_stat_generation
    ADD COLUMN generation_id BIGINT NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, generation_id),
    DROP INDEX UKgyh41v8qs5dkuqun8rc7qu3j4,
    ADD UNIQUE KEY uk_player_team_season_stat_generation_key
        (generation_id, player_id, team_id, league_id, season),
    ADD CONSTRAINT fk_player_team_season_stat_generation_generation
        FOREIGN KEY (generation_id) REFERENCES fixture_detail_generation (id);

-- Initial generation. Run while season-detail writers are paused so the copy is consistent.
INSERT INTO fixture_detail_generation
    (season, status, expected_fixture_count, normalized_at, aggregated_at, merged_at, published_at)
SELECT seasons.season, 'ACTIVE',
       (SELECT COUNT(*) FROM fixture f WHERE f.season = seasons.season),
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM (
    SELECT season FROM fixture WHERE season IS NOT NULL
    UNION
    SELECT season FROM player_team_season_stat
) seasons;

INSERT INTO fixture_generation
SELECT f.*, g.id
FROM fixture f
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO fixture_event_generation
SELECT e.*, g.id
FROM fixture_event e
JOIN fixture f ON f.id = e.fixture_id
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO fixture_lineup_generation
SELECT l.*, g.id
FROM fixture_lineup l
JOIN fixture f ON f.id = l.fixture_id
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO fixture_stat_generation
SELECT s.*, g.id
FROM fixture_stat s
JOIN fixture f ON f.id = s.fixture_id
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO player_fixture_stat_generation
SELECT s.*, g.id
FROM player_fixture_stat s
JOIN fixture f ON f.id = s.fixture_id
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO player_team_season_stat_generation
SELECT s.*, g.id
FROM player_team_season_stat s
JOIN fixture_detail_generation g ON g.season = s.season;

INSERT INTO fixture_detail_active_generation (season, generation_id)
SELECT season, id FROM fixture_detail_generation;

-- Each base-table writer records its fixture in the same transaction. Inserting into
-- the season row also serializes a later publish with concurrent live/admin writes.
CREATE TRIGGER trg_fixture_insert_detail_change BEFORE INSERT ON fixture
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, fixture_changes)
SELECT NEW.season, JSON_ARRAY(NEW.fixture_id) WHERE NEW.season IS NOT NULL
ON DUPLICATE KEY UPDATE fixture_changes = JSON_MERGE_PRESERVE(fixture_changes, VALUES(fixture_changes));

CREATE TRIGGER trg_fixture_update_detail_change BEFORE UPDATE ON fixture
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, fixture_changes, fixture_revision)
SELECT NEW.season, JSON_ARRAY(NEW.fixture_id), 1 WHERE NEW.season IS NOT NULL
ON DUPLICATE KEY UPDATE fixture_changes = JSON_MERGE_PRESERVE(fixture_changes, VALUES(fixture_changes)),
    fixture_revision = fixture_revision + 1;

-- This trigger follows the revision increment above, in the same base-row transaction.
CREATE TRIGGER trg_fixture_update_field_change BEFORE UPDATE ON fixture
FOR EACH ROW FOLLOWS trg_fixture_update_detail_change
INSERT INTO fixture_detail_fixture_field_change (season, fixture_id, field_name, revision)
SELECT NEW.season, NEW.fixture_id, 'away_coach_name', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_coach_name` <=> NEW.`away_coach_name`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_formation', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_formation` <=> NEW.`away_formation`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_goalkeeper_color_border', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_goalkeeper_color_border` <=> NEW.`away_goalkeeper_color_border`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_goalkeeper_color_number', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_goalkeeper_color_number` <=> NEW.`away_goalkeeper_color_number`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_goalkeeper_color_primary', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_goalkeeper_color_primary` <=> NEW.`away_goalkeeper_color_primary`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_player_color_border', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_player_color_border` <=> NEW.`away_player_color_border`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_player_color_number', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_player_color_number` <=> NEW.`away_player_color_number`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_player_color_primary', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_player_color_primary` <=> NEW.`away_player_color_primary`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_score` <=> NEW.`away_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_winner', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_winner` <=> NEW.`away_winner`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'elapsed', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`elapsed` <=> NEW.`elapsed`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'extratime_away_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`extratime_away_score` <=> NEW.`extratime_away_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'extratime_home_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`extratime_home_score` <=> NEW.`extratime_home_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'first_period', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`first_period` <=> NEW.`first_period`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'fixture_date', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`fixture_date` <=> NEW.`fixture_date`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'fixture_status', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`fixture_status` <=> NEW.`fixture_status`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'fulltime_away_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`fulltime_away_score` <=> NEW.`fulltime_away_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'fulltime_home_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`fulltime_home_score` <=> NEW.`fulltime_home_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'halftime_away_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`halftime_away_score` <=> NEW.`halftime_away_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'halftime_home_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`halftime_home_score` <=> NEW.`halftime_home_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_coach_name', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_coach_name` <=> NEW.`home_coach_name`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_formation', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_formation` <=> NEW.`home_formation`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_goalkeeper_color_border', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_goalkeeper_color_border` <=> NEW.`home_goalkeeper_color_border`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_goalkeeper_color_number', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_goalkeeper_color_number` <=> NEW.`home_goalkeeper_color_number`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_goalkeeper_color_primary', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_goalkeeper_color_primary` <=> NEW.`home_goalkeeper_color_primary`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_player_color_border', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_player_color_border` <=> NEW.`home_player_color_border`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_player_color_number', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_player_color_number` <=> NEW.`home_player_color_number`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_player_color_primary', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_player_color_primary` <=> NEW.`home_player_color_primary`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_score` <=> NEW.`home_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_winner', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_winner` <=> NEW.`home_winner`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'penalty_away_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`penalty_away_score` <=> NEW.`penalty_away_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'penalty_home_score', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`penalty_home_score` <=> NEW.`penalty_home_score`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'referee', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`referee` <=> NEW.`referee`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'round', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`round` <=> NEW.`round`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'second_period', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`second_period` <=> NEW.`second_period`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'status_long', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`status_long` <=> NEW.`status_long`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'status_short', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`status_short` <=> NEW.`status_short`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'timestamp', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`timestamp` <=> NEW.`timestamp`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'timezone', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`timezone` <=> NEW.`timezone`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'venue_city', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`venue_city` <=> NEW.`venue_city`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'venue_id', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`venue_id` <=> NEW.`venue_id`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'venue_name', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`venue_name` <=> NEW.`venue_name`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'away_team_id', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`away_team_id` <=> NEW.`away_team_id`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'home_team_id', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`home_team_id` <=> NEW.`home_team_id`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'league_id', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`league_id` <=> NEW.`league_id`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'venue_name_ko', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`venue_name_ko` <=> NEW.`venue_name_ko`)
UNION ALL
SELECT NEW.season, NEW.fixture_id, 'extra', season_lock.fixture_revision
FROM fixture_detail_season_lock season_lock
WHERE season_lock.season = NEW.season AND NOT (OLD.`extra` <=> NEW.`extra`)
ON DUPLICATE KEY UPDATE revision = VALUES(revision);

CREATE TRIGGER trg_fixture_delete_detail_change BEFORE DELETE ON fixture
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, fixture_changes)
SELECT OLD.season, JSON_ARRAY(OLD.fixture_id) WHERE OLD.season IS NOT NULL
ON DUPLICATE KEY UPDATE fixture_changes = JSON_MERGE_PRESERVE(fixture_changes, VALUES(fixture_changes));

CREATE TRIGGER trg_fixture_event_insert_detail_change BEFORE INSERT ON fixture_event
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, event_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE event_changes = JSON_MERGE_PRESERVE(event_changes, VALUES(event_changes));

CREATE TRIGGER trg_fixture_event_update_detail_change BEFORE UPDATE ON fixture_event
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, event_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE event_changes = JSON_MERGE_PRESERVE(event_changes, VALUES(event_changes));

CREATE TRIGGER trg_fixture_event_delete_detail_change BEFORE DELETE ON fixture_event
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, event_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = OLD.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE event_changes = JSON_MERGE_PRESERVE(event_changes, VALUES(event_changes));

CREATE TRIGGER trg_fixture_lineup_insert_detail_change BEFORE INSERT ON fixture_lineup
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, lineup_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE lineup_changes = JSON_MERGE_PRESERVE(lineup_changes, VALUES(lineup_changes));

CREATE TRIGGER trg_fixture_lineup_update_detail_change BEFORE UPDATE ON fixture_lineup
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, lineup_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE lineup_changes = JSON_MERGE_PRESERVE(lineup_changes, VALUES(lineup_changes));

CREATE TRIGGER trg_fixture_lineup_delete_detail_change BEFORE DELETE ON fixture_lineup
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, lineup_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = OLD.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE lineup_changes = JSON_MERGE_PRESERVE(lineup_changes, VALUES(lineup_changes));

CREATE TRIGGER trg_fixture_stat_insert_detail_change BEFORE INSERT ON fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, team_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE team_stat_changes = JSON_MERGE_PRESERVE(team_stat_changes, VALUES(team_stat_changes));

CREATE TRIGGER trg_fixture_stat_update_detail_change BEFORE UPDATE ON fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, team_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE team_stat_changes = JSON_MERGE_PRESERVE(team_stat_changes, VALUES(team_stat_changes));

CREATE TRIGGER trg_fixture_stat_delete_detail_change BEFORE DELETE ON fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, team_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = OLD.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE team_stat_changes = JSON_MERGE_PRESERVE(team_stat_changes, VALUES(team_stat_changes));

CREATE TRIGGER trg_player_fixture_stat_insert_detail_change BEFORE INSERT ON player_fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, player_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE player_stat_changes = JSON_MERGE_PRESERVE(player_stat_changes, VALUES(player_stat_changes));

CREATE TRIGGER trg_player_fixture_stat_update_detail_change BEFORE UPDATE ON player_fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, player_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = NEW.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE player_stat_changes = JSON_MERGE_PRESERVE(player_stat_changes, VALUES(player_stat_changes));

CREATE TRIGGER trg_player_fixture_stat_delete_detail_change BEFORE DELETE ON player_fixture_stat
FOR EACH ROW
INSERT INTO fixture_detail_season_lock (season, player_stat_changes)
SELECT f.season, JSON_ARRAY(f.fixture_id) FROM fixture f
    WHERE f.id = OLD.fixture_id AND f.season IS NOT NULL
ON DUPLICATE KEY UPDATE player_stat_changes = JSON_MERGE_PRESERVE(player_stat_changes, VALUES(player_stat_changes));

INSERT INTO fixture_detail_season_lock (season)
SELECT season FROM fixture_detail_generation;

INSERT INTO fixture_detail_generation_target (generation_id, fixture_id)
SELECT g.id, f.fixture_id
FROM fixture f
JOIN fixture_detail_generation g ON g.season = f.season;

INSERT INTO fixture_detail_generation_materialized (generation_id, fixture_id)
SELECT generation_id, fixture_id FROM fixture_detail_generation_target;

-- Verify before enabling generation-aware reads:
-- SELECT g.season, g.expected_fixture_count,
--        (SELECT COUNT(*) FROM fixture_generation f WHERE f.generation_id = g.id) AS copied_count
-- FROM fixture_detail_generation g;
