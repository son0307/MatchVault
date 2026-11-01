-- Source-table structure supplied for the fixture-detail migration review.
CREATE TABLE team (id BIGINT NOT NULL PRIMARY KEY, team_id BIGINT NOT NULL,
  name VARCHAR(255), korean_name VARCHAR(255));
CREATE TABLE player (id BIGINT NOT NULL PRIMARY KEY, position VARCHAR(255));
CREATE TABLE admin_field_override (target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL, field_name VARCHAR(80) NOT NULL,
  PRIMARY KEY (target_type, target_id, field_name));

CREATE TABLE fixture (
  id BIGINT NOT NULL AUTO_INCREMENT,
  away_coach_name VARCHAR(255), away_formation VARCHAR(20),
  away_goalkeeper_color_border VARCHAR(20), away_goalkeeper_color_number VARCHAR(20),
  away_goalkeeper_color_primary VARCHAR(20), away_player_color_border VARCHAR(20),
  away_player_color_number VARCHAR(20), away_player_color_primary VARCHAR(20),
  away_score INT, away_winner BIT(1), elapsed INT,
  extratime_away_score INT, extratime_home_score INT, first_period BIGINT,
  fixture_date DATETIME(6) NOT NULL, fixture_id BIGINT NOT NULL,
  fixture_status VARCHAR(20), fulltime_away_score INT, fulltime_home_score INT,
  halftime_away_score INT, halftime_home_score INT,
  home_coach_name VARCHAR(255), home_formation VARCHAR(20),
  home_goalkeeper_color_border VARCHAR(20), home_goalkeeper_color_number VARCHAR(20),
  home_goalkeeper_color_primary VARCHAR(20), home_player_color_border VARCHAR(20),
  home_player_color_number VARCHAR(20), home_player_color_primary VARCHAR(20),
  home_score INT, home_winner BIT(1), penalty_away_score INT, penalty_home_score INT,
  referee VARCHAR(255), round INT, season INT, second_period BIGINT,
  status_long VARCHAR(255), status_short VARCHAR(10), timestamp BIGINT,
  timezone VARCHAR(255), venue_city VARCHAR(255), venue_id BIGINT,
  venue_name VARCHAR(255), away_team_id BIGINT NOT NULL, home_team_id BIGINT NOT NULL,
  league_id INT NOT NULL DEFAULT 39, venue_name_ko VARCHAR(255),
  version BIGINT NOT NULL, extra INT,
  PRIMARY KEY (id),
  UNIQUE KEY UKln3yo17vwa3y32fislcqlx510 (fixture_id),
  KEY idx_fixture_season_date (season, fixture_date),
  KEY idx_fixture_season_status_date (season, fixture_status, fixture_date),
  KEY idx_fixture_home_season_date (home_team_id, season, fixture_date),
  KEY idx_fixture_away_season_date (away_team_id, season, fixture_date),
  KEY idx_fixture_league_season_date (league_id, season, fixture_date),
  CONSTRAINT FK2o7enkd358sqx3dtuyt80ua5e FOREIGN KEY (away_team_id) REFERENCES team (id),
  CONSTRAINT FKbhduiued9v9j0b3pqvbk3wye FOREIGN KEY (home_team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_event (
  id BIGINT NOT NULL AUTO_INCREMENT, comments VARCHAR(255), elapsed INT,
  event_detail VARCHAR(100), event_sequence INT NOT NULL, event_type VARCHAR(50),
  extra INT, assist_player_id BIGINT, fixture_id BIGINT NOT NULL,
  player_id BIGINT, team_id BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY UKt7y6s6f76p0v9janup7i34ptl (fixture_id, event_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_lineup (
  id BIGINT NOT NULL AUTO_INCREMENT, grid VARCHAR(10), is_starter BIT(1) NOT NULL,
  jersey_number INT NOT NULL, position VARCHAR(10) NOT NULL,
  fixture_id BIGINT NOT NULL, player_id BIGINT NOT NULL, team_id BIGINT NOT NULL,
  version BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK4jthokhjqite66ctpdm89fkbl (fixture_id, team_id, player_id),
  KEY idx_fixture_lineup_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fixture_stat (
  id BIGINT NOT NULL AUTO_INCREMENT, ball_possession INT, blocked_shots INT,
  corner_kicks INT, expected_goals DOUBLE, fouls INT, goalkeeper_saves INT,
  offsides INT, pass_accuracy INT, passes_accurate INT, red_cards INT,
  shots_inside_box INT, shots_off_goal INT, shots_on_goal INT,
  shots_outside_box INT, total_passes INT, total_shots INT, yellow_cards INT,
  fixture_id BIGINT NOT NULL, team_id BIGINT NOT NULL, version BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKtfbd3qeerduaejpyah6cjujkf (fixture_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE player_fixture_stat (
  id BIGINT NOT NULL AUTO_INCREMENT, assists INT, blocks INT, conceded INT,
  dribbles_attempts INT, dribbles_past INT, dribbles_success INT,
  duels_total INT, duels_won INT, fouls_committed INT, fouls_drawn INT,
  goals INT, interceptions INT, is_captain BIT(1), is_substitute BIT(1),
  minutes_played INT, offsides INT, pass_accuracy INT, passes_accurate INT,
  passes_key INT, passes_total INT, penalty_committed INT, penalty_missed INT,
  penalty_saved INT, penalty_scored INT, penalty_won INT, rating DOUBLE,
  red_cards INT, saves INT, shots_on_target INT, shots_total INT,
  tackles_total INT, yellow_cards INT, fixture_id BIGINT NOT NULL,
  player_id BIGINT NOT NULL, team_id BIGINT NOT NULL, version BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK7kikfer644vatjdnaa01qc9wh (fixture_id, player_id),
  KEY idx_player_fixture_stat_player_fixture (player_id, fixture_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE player_team_season_stat (
  id BIGINT NOT NULL AUTO_INCREMENT, appearances INT, assists INT,
  back_number INT, blocks INT, captain BIT(1), conceded INT,
  dribbles_attempts INT, dribbles_past INT, dribbles_success INT,
  duels_total INT, duels_won INT, fouls_committed INT, fouls_drawn INT,
  goals INT, interceptions INT, league_id BIGINT NOT NULL, lineups INT,
  minutes INT, pass_accuracy INT, passes_key INT, passes_total INT,
  penalty_committed INT, penalty_missed INT, penalty_saved INT,
  penalty_scored INT, penalty_won INT, position VARCHAR(255), rating DOUBLE,
  red_cards INT, saves INT, season INT NOT NULL, shots_on_target INT,
  shots_total INT, substitutes_bench INT, substitutes_in INT,
  substitutes_out INT, tackles_total INT, yellow_cards INT,
  yellow_red_cards INT, player_id BIGINT NOT NULL, team_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKgyh41v8qs5dkuqun8rc7qu3j4 (player_id, team_id, league_id, season),
  KEY idx_player_team_season_stat_player (player_id),
  KEY idx_player_team_season_stat_team_season (team_id, season),
  KEY idx_player_team_season_stat_league_season (league_id, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
