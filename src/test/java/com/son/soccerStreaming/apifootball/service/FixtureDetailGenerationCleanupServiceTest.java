package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class FixtureDetailGenerationCleanupServiceTest {

    @Test
    void removesOnlyExpiredFailedAndOlderRetiredGenerationsWithTheirChildren() throws Exception {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")
                .withCommand("--log-bin-trust-function-creators=1")) {
            mysql.start();
            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement statement = connection.createStatement()) {
                run(statement, Files.readString(Path.of("src/test/resources/sql/fixture-detail-source-schema.sql")));
                run(statement, """
                        INSERT INTO team VALUES (1,47,'Home','홈'), (2,49,'Away','원정');
                        INSERT INTO player VALUES (5,'Midfielder');
                        INSERT INTO fixture
                          (id, fixture_id, season, fixture_date, home_team_id, away_team_id, version)
                          VALUES (10,100,2025,'2025-01-01 00:00:00',1,2,0);
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
                run(statement, """
                        INSERT INTO fixture_detail_published_job
                          (job_id,season,generation_id,fixture_count)
                          VALUES ('completed-job',2025,1,1);
                        UPDATE fixture_detail_generation
                          SET status='RETIRED', published_at=DATE_SUB(NOW(), INTERVAL 30 DAY)
                          WHERE id=1;
                        INSERT INTO fixture_detail_generation
                          (id,season,status,expected_fixture_count,created_at,published_at)
                          VALUES
                          (2,2025,'RETIRED',1,DATE_SUB(NOW(), INTERVAL 20 DAY),DATE_SUB(NOW(), INTERVAL 20 DAY)),
                          (3,2025,'RETIRED',1,DATE_SUB(NOW(), INTERVAL 10 DAY),DATE_SUB(NOW(), INTERVAL 10 DAY)),
                          (4,2025,'ACTIVE',1,NOW(),NOW()),
                          (5,2025,'FAILED',1,DATE_SUB(NOW(), INTERVAL 8 DAY),NULL),
                          (6,2025,'FAILED',1,NOW(),NULL),
                          (7,2026,'RETIRED',0,DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_SUB(NOW(), INTERVAL 30 DAY)),
                          (8,2026,'RETIRED',0,NOW(),NOW()),
                          (9,2026,'RETIRED',0,NOW(),NOW()),
                          (10,2026,'ACTIVE',0,NOW(),NOW());
                        UPDATE fixture_detail_active_generation SET generation_id=4 WHERE season=2025;
                        INSERT INTO fixture_detail_active_generation (season,generation_id) VALUES (2026,10);
                        INSERT INTO fixture_detail_season_lock (season) VALUES (2026);
                        INSERT INTO fixture_detail_generation_target (generation_id,fixture_id) VALUES (5,100);
                        INSERT INTO fixture_detail_generation_response (generation_id,fixture_id,payload)
                          VALUES (5,100,'{}');
                        INSERT INTO fixture_detail_generation_materialized (generation_id,fixture_id)
                          VALUES (5,100);
                        INSERT INTO fixture_generation SELECT f.*,5 FROM fixture f;
                        INSERT INTO fixture_event_generation SELECT e.*,5 FROM fixture_event e;
                        INSERT INTO fixture_lineup_generation SELECT l.*,5 FROM fixture_lineup l;
                        INSERT INTO fixture_stat_generation SELECT s.*,5 FROM fixture_stat s;
                        INSERT INTO player_fixture_stat_generation SELECT s.*,5 FROM player_fixture_stat s;
                        INSERT INTO player_team_season_stat_generation SELECT s.*,5 FROM player_team_season_stat s;
                        """);
                var dataSource = new DriverManagerDataSource(
                        mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                var jdbc = new JdbcTemplate(dataSource);
                var cleanup = new FixtureDetailGenerationCleanupService(jdbc);
                var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

                assertThat(cleanup.findCandidates(7, 2, 5)).extracting(
                        FixtureDetailGenerationCleanupService.Candidate::id).containsExactly(1L, 5L);
                assertThatThrownBy(() -> transactions.execute(status -> cleanup.deleteIfEligible(
                        new FixtureDetailGenerationCleanupService.Candidate(1L, 2025), 7, 2)))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fixture_generation WHERE generation_id=1", Integer.class))
                        .isEqualTo(1);
                run(statement, Files.readString(Path.of("sql/fixture-detail-generation-cleanup.sql")));
                Boolean activeDeleted = transactions.execute(status -> cleanup.deleteIfEligible(
                        new FixtureDetailGenerationCleanupService.Candidate(4L, 2025), 7, 2));
                assertThat(activeDeleted).isFalse();
                Boolean recentlyRetiredDeleted = transactions.execute(status -> cleanup.deleteIfEligible(
                        new FixtureDetailGenerationCleanupService.Candidate(7L, 2026), 7, 2));
                assertThat(recentlyRetiredDeleted).isFalse();
                for (long id : List.of(1L, 5L)) {
                    Boolean deleted = transactions.execute(status -> cleanup.deleteIfEligible(
                            new FixtureDetailGenerationCleanupService.Candidate(id, 2025), 7, 2));
                    assertThat(deleted).isTrue();
                }
                assertThat(cleanup.findCandidates(7, 2, 5)).isEmpty();
                assertThat(jdbc.queryForList("SELECT id FROM fixture_detail_generation ORDER BY id", Long.class))
                        .containsExactly(2L, 3L, 4L, 6L, 7L, 8L, 9L, 10L);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture_detail_published_job", Integer.class))
                        .isEqualTo(1);
                for (String table : List.of("fixture_detail_generation_response", "fixture_detail_generation_target",
                        "fixture_detail_generation_materialized", "fixture_generation", "fixture_event_generation",
                        "fixture_lineup_generation", "fixture_stat_generation", "player_fixture_stat_generation",
                        "player_team_season_stat_generation")) {
                    assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE generation_id IN (1,5)", Integer.class))
                            .as(table).isZero();
                }
            }
        }
    }

    private static void run(Statement statement, String sql) throws Exception {
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }
}
