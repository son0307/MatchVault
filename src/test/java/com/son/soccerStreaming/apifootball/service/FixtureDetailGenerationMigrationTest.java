package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Executes the manual migration against the source tables' key layout on MySQL. */
@Testcontainers(disabledWithoutDocker = true)
class FixtureDetailGenerationMigrationTest {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Test
    void migratesTheProvidedSourceSchemaWithAllColumnsAndConstraints() throws Exception {
        try (MySQLContainer exactSchema = new MySQLContainer("mysql:8.4")
                .withCommand("--log-bin-trust-function-creators=1")) {
            exactSchema.start();
            try (Connection connection = DriverManager.getConnection(
                    exactSchema.getJdbcUrl(), exactSchema.getUsername(), exactSchema.getPassword());
                 Statement statement = connection.createStatement()) {
                run(statement, Files.readString(Path.of("src/test/resources/sql/fixture-detail-source-schema.sql")));
                run(statement, """
                        INSERT INTO team VALUES (1,47,'Home','홈'), (2,49,'Away','원정');
                        INSERT INTO player VALUES (5,'Midfielder');
                        INSERT INTO fixture
                          (id, fixture_id, season, fixture_date, home_team_id, away_team_id,
                           home_score, away_score, venue_name, version)
                          VALUES (10,100,2025,'2025-01-01 00:00:00',1,2,1,1,'Original',0);
                        INSERT INTO fixture_event (id,fixture_id,event_sequence) VALUES (20,10,1);
                        INSERT INTO fixture_lineup
                          (id,fixture_id,team_id,player_id,version,jersey_number,position,is_starter)
                          VALUES (30,10,1,5,0,7,'M',b'1');
                        INSERT INTO fixture_stat (id,fixture_id,team_id,version) VALUES (40,10,1,0);
                        INSERT INTO player_fixture_stat (id,fixture_id,player_id,team_id,version)
                          VALUES (50,10,5,1,0);
                        INSERT INTO player_team_season_stat (id,player_id,team_id,league_id,season)
                          VALUES (60,5,1,39,2025);
                        """);
                run(statement, Files.readString(Path.of("sql/fixture-detail-generations.sql")));
                run(statement, Files.readString(Path.of("sql/fixture-detail-generations-verify.sql")));
                assertThat(count(statement, "fixture_detail_active_generation")).isEqualTo(1);
                for (String table : new String[]{"fixture", "fixture_event", "fixture_lineup",
                        "fixture_stat", "player_fixture_stat", "player_team_season_stat"}) {
                    assertThat(count(statement, table + "_generation")).as(table).isEqualTo(1);
                }
                var dataSource = new DriverManagerDataSource(
                        exactSchema.getJdbcUrl(), exactSchema.getUsername(), exactSchema.getPassword());
                var jdbc = new JdbcTemplate(dataSource);
                var store = new FixtureDetailGenerationStore(jdbc, mock(ObjectMapper.class),
                        new FixtureDetailFixtureFieldStore(jdbc));
                statement.executeUpdate("UPDATE fixture SET venue_city = 'Before batch' WHERE fixture_id = 100");
                long generationId = store.create(2025, List.of(100L));
                statement.executeUpdate("INSERT INTO fixture_generation SELECT f.*, " + generationId
                        + " FROM fixture f");
                for (String table : new String[]{"fixture_event", "fixture_lineup", "fixture_stat",
                        "player_fixture_stat", "player_team_season_stat"}) {
                    statement.executeUpdate("INSERT INTO " + table + "_generation SELECT row_data.*, "
                            + generationId + " FROM " + table + " row_data");
                }
                statement.executeUpdate("UPDATE fixture_generation SET home_score = 2, venue_name = 'Batch',"
                        + " venue_city = 'Batch city' "
                        + "WHERE generation_id = " + generationId);
                statement.executeUpdate("INSERT INTO fixture_detail_generation_response"
                        + " (generation_id,fixture_id,payload) VALUES (" + generationId + ",100,'{}')");
                statement.executeUpdate("INSERT INTO fixture_detail_generation_materialized"
                        + " (generation_id,fixture_id) VALUES (" + generationId + ",100)");
                statement.executeUpdate("UPDATE fixture_detail_generation SET normalized_at = CURRENT_TIMESTAMP(6),"
                        + " aggregated_at = CURRENT_TIMESTAMP(6) WHERE id = " + generationId);
                statement.executeUpdate("INSERT INTO admin_field_override VALUES ('FIXTURE',100,'venueName')");
                statement.executeUpdate("UPDATE fixture SET venue_name = 'Admin' WHERE fixture_id = 100");
                var fields = new FixtureDetailFixtureFieldStore(jdbc);
                var publisher = new FixtureDetailGenerationPublisher(jdbc, store,
                        new FixtureDetailGenerationAggregationService(jdbc), fields);
                new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                        .executeWithoutResult(status -> publisher.publish(2025, generationId));
                try (ResultSet merged = statement.executeQuery("SELECT home_score, venue_name, venue_city "
                        + "FROM fixture_generation WHERE generation_id = " + generationId)) {
                    assertThat(merged.next()).isTrue();
                    assertThat(merged.getInt("home_score")).isEqualTo(2);
                    assertThat(merged.getString("venue_name")).isEqualTo("Admin");
                    assertThat(merged.getString("venue_city")).isEqualTo("Batch city");
                }
                statement.executeUpdate("UPDATE fixture SET venue_city = 'New city' WHERE fixture_id = 100");
                TeamRepository teams = mock(TeamRepository.class);
                when(teams.findById(1L)).thenReturn(Optional.of(Team.builder().id(1L).teamId(47L).build()));
                when(teams.findById(2L)).thenReturn(Optional.of(Team.builder().id(2L).teamId(49L).build()));
                var reader = new FixtureDetailGenerationReadStore(jdbc, teams,
                        mock(PlayerRepository.class), mock(FixtureRepository.class), fields);
                var visible = reader.findFixture(generationId, 100L).orElseThrow();
                assertThat(visible.getHomeScore()).isEqualTo(2);
                assertThat(visible.getVenueName()).isEqualTo("Admin");
                assertThat(visible.getVenueCity()).isEqualTo("New city");
                new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                        .executeWithoutResult(status -> assertThat(publisher.projectLiveChanges(2025)).isTrue());
                try (ResultSet projected = statement.executeQuery("SELECT home_score, venue_city "
                        + "FROM fixture_generation WHERE generation_id = " + generationId)) {
                    assertThat(projected.next()).isTrue();
                    assertThat(projected.getInt("home_score")).isEqualTo(2);
                    assertThat(projected.getString("venue_city")).isEqualTo("New city");
                }
                statement.executeUpdate("ALTER TABLE player ADD COLUMN player_id BIGINT");
                statement.executeUpdate("UPDATE player SET player_id = 5 WHERE id = 5");
                var rankingReader = new FixtureDetailGenerationRankingReadStore(jdbc,
                        mock(PlayerRepository.class), teams);
                assertThat(rankingReader.latestTeams(generationId, 2025, List.of(5L)))
                        .singleElement()
                        .extracting(team -> team.getTeamId())
                        .isEqualTo(47L);

                // A later chunk can fail after an earlier chunk committed privately.
                statement.executeUpdate("""
                        INSERT INTO fixture
                          (id, fixture_id, season, fixture_date, home_team_id, away_team_id, version)
                        VALUES (11,101,2025,'2025-01-02 00:00:00',1,2,0)
                        """);
                var failedStore = new FixtureDetailGenerationStore(jdbc, new ObjectMapper(), fields);
                long failedGeneration = failedStore.create(2025, List.of(100L, 101L));
                ApiFootballLiveDto.FixtureResponse firstChunk = new ObjectMapper().readValue(
                        """
                        {"fixture":{"id":100},"players":[{"team":{"id":47},"players":[
                          {"player":{"id":0,"name":"Younes Ibrahim"},"statistics":[{"games":{"minutes":0}}]}
                        ]}]}
                        """, ApiFootballLiveDto.FixtureResponse.class);
                failedStore.stageChunk(failedGeneration, List.of(100L), List.of(firstChunk));
                assertThat(new FixtureDetailAnonymousPlayerStatReader(jdbc, new ObjectMapper())
                        .findForFixture(failedGeneration, 100L)).singleElement().satisfies(anonymous -> {
                    assertThat(anonymous.teamId()).isEqualTo(47L);
                    assertThat(anonymous.stat().getPlayerId()).isNull();
                    assertThat(anonymous.stat().getPlayerName()).isEqualTo("Younes Ibrahim");
                });
                assertThatThrownBy(() -> failedStore.stageChunk(failedGeneration, List.of(101L), List.of()))
                        .isInstanceOf(IllegalArgumentException.class);
                failedStore.markFailed(failedGeneration);
                assertThat(failedStore.activeGenerationId(2025)).isEqualTo(generationId);
                assertThat(jdbc.queryForObject("""
                        SELECT status FROM fixture_detail_generation WHERE id = ?
                        """, String.class, failedGeneration)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM fixture_generation WHERE generation_id = ?
                        """, Integer.class, failedGeneration)).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM fixture_detail_generation_response WHERE generation_id = ?
                        """, Integer.class, failedGeneration)).isEqualTo(1);
            }
        }
    }

    @Test
    void copiesExistingSeasonAndAllowsSameNaturalKeysInAnotherGeneration() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            run(statement, """
                    CREATE TABLE team (id BIGINT NOT NULL PRIMARY KEY, team_id BIGINT NOT NULL,
                      name VARCHAR(255), korean_name VARCHAR(255));
                    CREATE TABLE player (id BIGINT NOT NULL PRIMARY KEY, position VARCHAR(255));
                    CREATE TABLE admin_field_override (target_type VARCHAR(20) NOT NULL,
                      target_id BIGINT NOT NULL, field_name VARCHAR(80) NOT NULL,
                      PRIMARY KEY (target_type, target_id, field_name));
                    CREATE TABLE fixture (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      fixture_id BIGINT NOT NULL, season INT, fixture_date DATETIME(6) NOT NULL,
                      home_team_id BIGINT NOT NULL, away_team_id BIGINT NOT NULL,
                      referee VARCHAR(255), timezone VARCHAR(255), timestamp BIGINT,
                      first_period BIGINT, second_period BIGINT, round INT, league_id INT DEFAULT 39,
                      venue_id BIGINT, venue_name VARCHAR(255), venue_name_ko VARCHAR(255), venue_city VARCHAR(255),
                      status_short VARCHAR(10), status_long VARCHAR(255), elapsed INT, extra INT,
                      fixture_status VARCHAR(20), home_score INT, away_score INT,
                      home_winner BIT(1), away_winner BIT(1),
                      halftime_home_score INT, halftime_away_score INT,
                      fulltime_home_score INT, fulltime_away_score INT,
                      extratime_home_score INT, extratime_away_score INT,
                      penalty_home_score INT, penalty_away_score INT,
                      home_formation VARCHAR(20), away_formation VARCHAR(20),
                      home_coach_name VARCHAR(255), away_coach_name VARCHAR(255),
                      home_player_color_primary VARCHAR(20), home_player_color_number VARCHAR(20),
                      home_player_color_border VARCHAR(20),
                      home_goalkeeper_color_primary VARCHAR(20), home_goalkeeper_color_number VARCHAR(20),
                      home_goalkeeper_color_border VARCHAR(20),
                      away_player_color_primary VARCHAR(20), away_player_color_number VARCHAR(20),
                      away_player_color_border VARCHAR(20),
                      away_goalkeeper_color_primary VARCHAR(20), away_goalkeeper_color_number VARCHAR(20),
                      away_goalkeeper_color_border VARCHAR(20), version BIGINT NOT NULL DEFAULT 0,
                      UNIQUE KEY UKln3yo17vwa3y32fislcqlx510 (fixture_id)
                    );
                    CREATE TABLE fixture_event (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, fixture_id BIGINT NOT NULL,
                      event_sequence INT NOT NULL,
                      UNIQUE KEY UKt7y6s6f76p0v9janup7i34ptl (fixture_id,event_sequence)
                    );
                    CREATE TABLE fixture_lineup (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, fixture_id BIGINT NOT NULL,
                      team_id BIGINT NOT NULL, player_id BIGINT NOT NULL,
                      UNIQUE KEY UK4jthokhjqite66ctpdm89fkbl (fixture_id,team_id,player_id)
                    );
                    CREATE TABLE fixture_stat (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, fixture_id BIGINT NOT NULL,
                      team_id BIGINT NOT NULL,
                      UNIQUE KEY UKtfbd3qeerduaejpyah6cjujkf (fixture_id,team_id)
                    );
                    CREATE TABLE player_fixture_stat (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, fixture_id BIGINT NOT NULL,
                      player_id BIGINT NOT NULL, team_id BIGINT NOT NULL,
                      UNIQUE KEY UK7kikfer644vatjdnaa01qc9wh (fixture_id,player_id)
                    );
                    CREATE TABLE player_team_season_stat (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, player_id BIGINT NOT NULL,
                      team_id BIGINT NOT NULL, league_id BIGINT NOT NULL, season INT NOT NULL,
                      UNIQUE KEY UKgyh41v8qs5dkuqun8rc7qu3j4 (player_id,team_id,league_id,season)
                    );
                    INSERT INTO team VALUES (1,47,'Home','홈'), (2,49,'Away','원정');
                    INSERT INTO player VALUES (5,'Midfielder');
                    INSERT INTO fixture (id,fixture_id,season,fixture_date,home_team_id,away_team_id,
                      fixture_status,home_score,away_score)
                      VALUES (10,100,2025,'2025-01-01 00:00:00',1,2,'FINISHED',2,1);
                    INSERT INTO fixture_event (id,fixture_id,event_sequence) VALUES (20,10,1);
                    INSERT INTO fixture_lineup (id,fixture_id,team_id,player_id) VALUES (30,10,1,5);
                    INSERT INTO fixture_stat (id,fixture_id,team_id) VALUES (40,10,1);
                    INSERT INTO player_fixture_stat (id,fixture_id,player_id,team_id) VALUES (50,10,5,1);
                    INSERT INTO player_team_season_stat (id,player_id,team_id,league_id,season)
                      VALUES (60,5,1,39,2025);
                    """);
            extendAggregateSchema(statement);
            run(statement, Files.readString(Path.of("sql/fixture-detail-generations.sql")));
            run(statement, Files.readString(Path.of("sql/fixture-detail-generations-verify.sql")));

            assertThat(count(statement, "fixture_detail_active_generation")).isEqualTo(1);
            for (String table : new String[]{"fixture", "fixture_event", "fixture_lineup", "fixture_stat",
                    "player_fixture_stat", "player_team_season_stat"}) {
                assertThat(count(statement, table + "_generation")).as(table).isEqualTo(1);
            }
            statement.executeUpdate("""
                    INSERT INTO fixture_detail_generation (season,status,expected_fixture_count)
                    VALUES (2025,'BUILDING',1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO fixture_generation
                    SELECT f.*, (SELECT MAX(id) FROM fixture_detail_generation) FROM fixture f
                    """);
            assertThat(count(statement, "fixture_generation")).isEqualTo(2);

            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            FixtureDetailGenerationStore store = new FixtureDetailGenerationStore(
                    new JdbcTemplate(dataSource), new ObjectMapper(),
                    new FixtureDetailFixtureFieldStore(new JdbcTemplate(dataSource)));
            long nextGeneration = store.create(2025, java.util.List.of(100L));
            statement.executeUpdate("INSERT INTO fixture_generation SELECT f.*, " + nextGeneration + " FROM fixture f");
            statement.executeUpdate("""
                    INSERT INTO fixture_detail_generation_response (generation_id, fixture_id, payload)
                    VALUES (%d, 100, '{}')
                    """.formatted(nextGeneration));
            statement.executeUpdate("""
                    INSERT INTO fixture_detail_generation_materialized (generation_id, fixture_id)
                    VALUES (%d, 100)
                    """.formatted(nextGeneration));
            statement.executeUpdate("""
                    UPDATE fixture_detail_generation SET normalized_at = CURRENT_TIMESTAMP(6),
                      aggregated_at = CURRENT_TIMESTAMP(6), merged_at = CURRENT_TIMESTAMP(6)
                    WHERE id = %d
                    """.formatted(nextGeneration));

            store.markReady(nextGeneration);
            store.publish(2025, nextGeneration);
            store.publish(2025, nextGeneration);
            assertThat(store.activeGenerationId(2025)).isEqualTo(nextGeneration);
            assertThatThrownBy(() -> store.publish(2025, 1L))
                    .isInstanceOf(IllegalStateException.class);

            statement.executeUpdate("UPDATE fixture SET fixture_date = '2025-01-02 00:00:00' WHERE id = 10");
            statement.executeUpdate("UPDATE fixture_event SET event_sequence = 2 WHERE id = 20");
            try (ResultSet changes = statement.executeQuery("""
                    SELECT JSON_LENGTH(fixture_changes), JSON_LENGTH(event_changes)
                    FROM fixture_detail_season_lock WHERE season = 2025
                    """)) {
                changes.next();
                assertThat(changes.getInt(1)).isEqualTo(1);
                assertThat(changes.getInt(2)).isEqualTo(1);
            }

            connection.setAutoCommit(false);
            statement.executeUpdate("UPDATE fixture SET fixture_date = '2025-01-03 00:00:00' WHERE id = 10");
            connection.rollback();
            try (ResultSet changes = statement.executeQuery("""
                    SELECT JSON_LENGTH(fixture_changes) FROM fixture_detail_season_lock WHERE season = 2025
                    """)) {
                changes.next();
                assertThat(changes.getInt(1)).isEqualTo(1);
            }
            connection.setAutoCommit(true);

            long mergedGeneration = store.create(2025, java.util.List.of(100L));
            statement.executeUpdate("INSERT INTO fixture_generation SELECT f.*, " + mergedGeneration + " FROM fixture f");
            statement.executeUpdate("INSERT INTO fixture_event_generation SELECT e.*, "
                    + mergedGeneration + " FROM fixture_event e");
            statement.executeUpdate("INSERT INTO fixture_lineup_generation SELECT l.*, "
                    + mergedGeneration + " FROM fixture_lineup l");
            statement.executeUpdate("INSERT INTO fixture_stat_generation SELECT s.*, "
                    + mergedGeneration + " FROM fixture_stat s");
            statement.executeUpdate("INSERT INTO player_fixture_stat_generation SELECT s.*, "
                    + mergedGeneration + " FROM player_fixture_stat s");
            statement.executeUpdate("INSERT INTO player_team_season_stat_generation SELECT s.*, "
                    + mergedGeneration + " FROM player_team_season_stat s");
            statement.executeUpdate("INSERT INTO fixture_detail_generation_response"
                    + " (generation_id,fixture_id,payload) VALUES (" + mergedGeneration + ",100,'{}')");
            statement.executeUpdate("INSERT INTO fixture_detail_generation_materialized"
                    + " (generation_id,fixture_id) VALUES (" + mergedGeneration + ",100)");
            statement.executeUpdate("UPDATE fixture_detail_generation SET normalized_at = CURRENT_TIMESTAMP(6),"
                    + " aggregated_at = CURRENT_TIMESTAMP(6) WHERE id = " + mergedGeneration);
            // The private batch still has its earlier event; a committed base-table
            // change must replace it before the pointer moves.
            statement.executeUpdate("UPDATE fixture_event_generation SET event_sequence = 1"
                    + " WHERE generation_id = " + mergedGeneration);
            FixtureDetailGenerationAggregationService aggregation =
                    new FixtureDetailGenerationAggregationService(new JdbcTemplate(dataSource));
            FixtureDetailGenerationPublisher publisher =
                    new FixtureDetailGenerationPublisher(new JdbcTemplate(dataSource), store, aggregation,
                            new FixtureDetailFixtureFieldStore(new JdbcTemplate(dataSource)));
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> publisher.publish(
                            2025, mergedGeneration, "scheduled-fixture-2025", 1));
            assertThat(store.activeGenerationId(2025)).isEqualTo(mergedGeneration);
            assertThat(store.publishedJobFixtureCount("scheduled-fixture-2025")).isEqualTo(1);
            try (ResultSet stats = statement.executeQuery("""
                    SELECT COUNT(*) FROM player_team_season_stat_generation
                    WHERE generation_id = %d AND player_id = 5 AND team_id = 1
                    """.formatted(mergedGeneration))) {
                stats.next();
                assertThat(stats.getLong(1)).isEqualTo(1);
            }
            try (ResultSet merged = statement.executeQuery("SELECT event_sequence FROM fixture_event_generation"
                    + " WHERE generation_id = " + mergedGeneration)) {
                merged.next();
                assertThat(merged.getInt(1)).isEqualTo(2);
            }
            try (ResultSet changes = statement.executeQuery("""
                    SELECT JSON_LENGTH(fixture_changes), JSON_LENGTH(event_changes)
                    FROM fixture_detail_season_lock WHERE season = 2025
                    """)) {
                changes.next();
                assertThat(changes.getInt(1)).isZero();
                assertThat(changes.getInt(2)).isZero();
            }
            statement.executeUpdate("UPDATE fixture SET fixture_date = '2025-01-04 00:00:00' WHERE id = 10");
            TeamRepository teams = mock(TeamRepository.class);
            when(teams.findById(1L)).thenReturn(Optional.of(Team.builder().id(1L).teamId(47L).build()));
            when(teams.findById(2L)).thenReturn(Optional.of(Team.builder().id(2L).teamId(49L).build()));
            FixtureDetailGenerationReadStore reader = new FixtureDetailGenerationReadStore(
                    new JdbcTemplate(dataSource), teams, mock(PlayerRepository.class),
                    mock(FixtureRepository.class),
                    new FixtureDetailFixtureFieldStore(new JdbcTemplate(dataSource)));
            assertThat(reader.findRecentFixtures(List.of(mergedGeneration), null, 2025,
                    null, null, null, null, 20)).singleElement()
                    .extracting(fixture -> fixture.getFixtureDate())
                    .isEqualTo(LocalDateTime.of(2025, 1, 4, 0, 0));
            assertThat(reader.meta(mergedGeneration, 2025).maxDate())
                    .isEqualTo(LocalDateTime.of(2025, 1, 4, 0, 0));
            assertThat(reader.findHeadToHead(List.of(mergedGeneration), 39, 47L, 49L,
                    List.of("FINISHED", "FT"), 10)).singleElement()
                    .extracting(fixture -> fixture.getFixtureDate())
                    .isEqualTo(LocalDateTime.of(2025, 1, 4, 0, 0));
            assertThat(reader.searchFixtures(List.of(mergedGeneration), List.of("home"), 10))
                    .singleElement().extracting(fixture -> fixture.getFixtureId()).isEqualTo(100L);
            FixtureDetailLiveChangeStore liveChanges =
                    new FixtureDetailLiveChangeStore(new JdbcTemplate(dataSource));
            assertThat(liveChanges.changed(2025, 100L, FixtureDetailLiveChangeStore.Part.FIXTURE)).isTrue();
            assertThat(liveChanges.changed(2025, 100L, FixtureDetailLiveChangeStore.Part.EVENT)).isFalse();
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> publisher.publish(
                            2025, mergedGeneration, "scheduled-fixture-2025", 1));
            try (ResultSet changes = statement.executeQuery("""
                    SELECT JSON_LENGTH(fixture_changes) FROM fixture_detail_season_lock WHERE season = 2025
                    """)) {
                changes.next();
                assertThat(changes.getInt(1)).isEqualTo(1);
            }
            long nextPrivate = store.create(2025, List.of(100L));
            ApiFootballLiveDto.FixtureResponse response = new ObjectMapper().readValue(
                    "{\"fixture\":{\"id\":100}}", ApiFootballLiveDto.FixtureResponse.class);
            store.stageChunk(nextPrivate, List.of(100L), List.of(response));
            try (ResultSet copied = statement.executeQuery("""
                    SELECT fixture_date FROM fixture_generation WHERE generation_id = %d
                    """.formatted(nextPrivate))) {
                assertThat(copied.next()).isTrue();
                assertThat(copied.getTimestamp(1).toLocalDateTime())
                        .isEqualTo(LocalDateTime.of(2025, 1, 4, 0, 0));
            }
            statement.executeUpdate("UPDATE player_fixture_stat SET minutes_played = 90, goals = 2 WHERE id = 50");
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> assertThat(publisher.projectLiveChanges(2025)).isTrue());
            assertThat(store.activeLiveRevision(2025)).isEqualTo(1L);
            statement.executeUpdate("""
                    INSERT INTO fixture (id,fixture_id,season,fixture_date,home_team_id,away_team_id)
                    VALUES (11,101,2026,'2026-01-01 00:00:00',1,2)
                    """);
            statement.executeUpdate("INSERT INTO fixture_event (id,fixture_id,event_sequence) VALUES (21,11,1)");
            long firstNewSeasonGeneration = store.create(2026, List.of(101L));
            ApiFootballLiveDto.FixtureResponse firstNewSeasonResponse = new ObjectMapper().readValue(
                    "{\"fixture\":{\"id\":101}}", ApiFootballLiveDto.FixtureResponse.class);
            store.stageChunk(firstNewSeasonGeneration, List.of(101L), List.of(firstNewSeasonResponse));
            assertThat(store.activeGenerationId(2026)).isNull();
            assertThat(count(statement, "fixture_generation WHERE generation_id = " + firstNewSeasonGeneration))
                    .isEqualTo(1);
            assertThat(count(statement, "fixture_event_generation WHERE generation_id = " + firstNewSeasonGeneration))
                    .isEqualTo(1);
            store.markFixtureMaterialized(firstNewSeasonGeneration, 101L);
            store.markNormalized(firstNewSeasonGeneration);
            aggregation.rebuild(firstNewSeasonGeneration, 2026);
            store.markAggregated(firstNewSeasonGeneration);
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> publisher.publish(
                            2026, firstNewSeasonGeneration, "scheduled-fixture-2026", 1));
            assertThat(store.activeGenerationId(2026)).isEqualTo(firstNewSeasonGeneration);
            assertThat(store.publishedJobFixtureCount("scheduled-fixture-2026")).isEqualTo(1);
            try (ResultSet projected = statement.executeQuery("""
                    SELECT goals FROM player_team_season_stat_generation
                    WHERE generation_id = %d AND player_id = 5 AND team_id = 1
                    """.formatted(mergedGeneration))) {
                assertThat(projected.next()).isTrue();
                assertThat(projected.getInt(1)).isEqualTo(2);
            }
            new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> assertThat(publisher.projectLiveChanges(2025)).isFalse());
            assertThat(store.activeLiveRevision(2025)).isEqualTo(1L);

            // A publisher holds the season row while switching generations. A base-table
            // writer must wait at its trigger, then record the write after that commit.
            connection.setAutoCommit(false);
            statement.executeQuery("SELECT season FROM fixture_detail_season_lock"
                    + " WHERE season = 2025 FOR UPDATE").close();
            var executor = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch writerStarted = new CountDownLatch(1);
                var writer = executor.submit(() -> {
                    try (Connection writerConnection = DriverManager.getConnection(
                            mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                         Statement writerStatement = writerConnection.createStatement()) {
                        writerStarted.countDown();
                        writerStatement.executeUpdate("UPDATE fixture SET home_score = 3 WHERE id = 10");
                        return true;
                    }
                });
                assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(250);
                assertThat(writer.isDone()).isFalse();
                connection.commit();
                assertThat(writer.get(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
                executor.shutdownNow();
            }
            assertThat(liveChanges.changed(2025, 100L,
                    FixtureDetailLiveChangeStore.Part.FIXTURE)).isTrue();
        }
    }

    private static void run(Statement statement, String sql) throws Exception {
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }

    private static void extendAggregateSchema(Statement statement) throws Exception {
        String[] sums = ("shots_total shots_on_target goals conceded assists saves passes_total passes_key "
                + "tackles_total blocks interceptions duels_total duels_won dribbles_attempts "
                + "dribbles_success dribbles_past fouls_drawn fouls_committed red_cards "
                + "penalty_won penalty_committed penalty_scored penalty_missed penalty_saved").split(" ");
        for (String column : sums) {
            statement.executeUpdate("ALTER TABLE player_fixture_stat ADD COLUMN " + column + " INT");
            statement.executeUpdate("ALTER TABLE player_team_season_stat ADD COLUMN " + column + " INT");
        }
        for (String column : "minutes_played passes_accurate yellow_cards".split(" ")) {
            statement.executeUpdate("ALTER TABLE player_fixture_stat ADD COLUMN " + column + " INT");
        }
        statement.executeUpdate("ALTER TABLE player_fixture_stat ADD COLUMN rating DOUBLE");
        statement.executeUpdate("ALTER TABLE player_fixture_stat ADD COLUMN is_substitute BIT(1)");
        statement.executeUpdate("ALTER TABLE player_fixture_stat ADD COLUMN is_captain BIT(1)");
        statement.executeUpdate("ALTER TABLE fixture_lineup ADD COLUMN jersey_number INT");
        statement.executeUpdate("ALTER TABLE fixture_lineup ADD COLUMN position VARCHAR(20)");
        for (String column : ("back_number appearances lineups minutes substitutes_in substitutes_out "
                + "substitutes_bench pass_accuracy yellow_cards yellow_red_cards").split(" ")) {
            statement.executeUpdate("ALTER TABLE player_team_season_stat ADD COLUMN " + column + " INT");
        }
        statement.executeUpdate("ALTER TABLE player_team_season_stat ADD COLUMN position VARCHAR(255)");
        statement.executeUpdate("ALTER TABLE player_team_season_stat ADD COLUMN rating DOUBLE");
        statement.executeUpdate("ALTER TABLE player_team_season_stat ADD COLUMN captain BIT(1)");
    }

    private static long count(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
