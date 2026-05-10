package com.son.soccerStreaming.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkDataService {

    private static final int PLAYERS_PER_TEAM_IN_MATCH = 15;
    private static final long BULK_FIXTURE_ID_START = 9_000_000L;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public BulkInsertResult bulkInsertPlayerStats(int targetStatCount) {
        List<TeamSeed> teams = loadTeams();
        if (teams.size() < 2) {
            throw new IllegalStateException("벌크 데이터를 생성하려면 최소 2개 이상의 팀이 필요합니다.");
        }

        Map<Long, List<PlayerSeed>> playersByTeam = loadPlayersByTeam();
        teams.forEach(team -> {
            List<PlayerSeed> players = playersByTeam.getOrDefault(team.id(), List.of());
            if (players.size() < PLAYERS_PER_TEAM_IN_MATCH) {
                throw new IllegalStateException("팀별 최소 " + PLAYERS_PER_TEAM_IN_MATCH + "명의 선수가 필요합니다. teamId=" + team.id());
            }
        });

        int statsPerMatch = PLAYERS_PER_TEAM_IN_MATCH * 2;
        int fixtureCount = Math.max(1, (int) Math.ceil(targetStatCount / (double) statsPerMatch));

        Random random = new Random();
        long nextFixtureId = nextBulkFixtureId();
        long startTime = System.currentTimeMillis();

        int insertedStats = 0;
        int insertedLineups = 0;

        for (int i = 0; i < fixtureCount; i++) {
            TeamSeed homeTeam = teams.get(i % teams.size());
            TeamSeed awayTeam = teams.get((i + 1) % teams.size());

            Long matchRecordId = insertMatchRecord(nextFixtureId++, homeTeam, awayTeam, i, random);

            List<PlayerSeed> homePlayers = playersByTeam.get(homeTeam.id()).stream()
                    .limit(PLAYERS_PER_TEAM_IN_MATCH)
                    .toList();
            List<PlayerSeed> awayPlayers = playersByTeam.get(awayTeam.id()).stream()
                    .limit(PLAYERS_PER_TEAM_IN_MATCH)
                    .toList();

            insertedLineups += insertLineups(matchRecordId, homeTeam.id(), homePlayers);
            insertedLineups += insertLineups(matchRecordId, awayTeam.id(), awayPlayers);
            insertedStats += insertPlayerStats(matchRecordId, homeTeam.id(), homePlayers, random);
            insertedStats += insertPlayerStats(matchRecordId, awayTeam.id(), awayPlayers, random);
        }

        long elapsedMillis = System.currentTimeMillis() - startTime;
        log.info("벌크 데이터 생성 완료. matches={}, lineups={}, stats={}, elapsed={}ms",
                fixtureCount, insertedLineups, insertedStats, elapsedMillis);

        return new BulkInsertResult(fixtureCount, insertedLineups, insertedStats, elapsedMillis);
    }

    private List<TeamSeed> loadTeams() {
        String sql = """
                SELECT t.id, t.team_id, t.name, t.code,
                       v.venue_id, v.venue_name, v.venue_city
                FROM team t
                LEFT JOIN venue v ON t.venue_id = v.id
                ORDER BY t.id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TeamSeed(
                rs.getLong("id"),
                rs.getLong("team_api_id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getLong("venue_api_id"),
                rs.getString("venue_name"),
                rs.getString("venue_city")
        ));
    }

    private Map<Long, List<PlayerSeed>> loadPlayersByTeam() {
        String sql = """
                SELECT id, team_id, default_number, position
                FROM player
                ORDER BY team_id, default_number, id
                """;

        List<PlayerSeed> players = jdbcTemplate.query(sql, (rs, rowNum) -> new PlayerSeed(
                rs.getLong("id"),
                rs.getLong("team_id"),
                rs.getInt("default_number"),
                rs.getString("position")
        ));

        return players.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PlayerSeed::teamId,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparing(PlayerSeed::number, Comparator.nullsLast(Integer::compareTo)))
                                        .toList()
                        )
                ));
    }

    private long nextBulkFixtureId() {
        Long maxFixtureId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(fixture_id), ?) FROM fixture",
                Long.class,
                BULK_FIXTURE_ID_START - 1
        );

        return Math.max(BULK_FIXTURE_ID_START, maxFixtureId + 1);
    }

    private Long insertMatchRecord(long fixtureId, TeamSeed homeTeam, TeamSeed awayTeam, int index, Random random) {
        String sql = """
                INSERT INTO fixture
                (fixture_id, home_team_id, away_team_id, fixture_date, referee, round,
                 venue_id, venue_name, venue_city, status_short, status_long, elapsed,
                 fixture_status, home_score, away_score, home_formation, away_formation,
                 home_coach_name, away_coach_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, fixtureId);
            ps.setLong(2, homeTeam.id());
            ps.setLong(3, awayTeam.id());
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now().minusDays(index % 180L)));
            ps.setString(5, "Bulk Referee " + (index % 12 + 1));
            ps.setString(6, "Regular Season - " + (index % 38 + 1));
            ps.setLong(7, homeTeam.venueId());
            ps.setString(8, homeTeam.venueName());
            ps.setString(9, homeTeam.venueCity());
            ps.setString(10, "FT");
            ps.setString(11, "Match Finished");
            ps.setInt(12, 90);
            ps.setString(13, "FINISHED");
            ps.setInt(14, random.nextInt(5));
            ps.setInt(15, random.nextInt(5));
            ps.setString(16, randomFormation(random));
            ps.setString(17, randomFormation(random));
            ps.setString(18, homeTeam.code() + " Coach");
            ps.setString(19, awayTeam.code() + " Coach");
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("fixture generated key를 가져오지 못했습니다.");
        }
        return generatedId.longValue();
    }

    private int insertLineups(Long matchRecordId, Long teamId, List<PlayerSeed> players) {
        String sql = """
                INSERT INTO fixture_lineup
                (fixture_id, team_id, player_id, jersey_number, position, grid, is_starter)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        int[][] results = jdbcTemplate.batchUpdate(sql, players, players.size(), (ps, player) -> {
            boolean starter = players.indexOf(player) < 11;
            ps.setLong(1, matchRecordId);
            ps.setLong(2, teamId);
            ps.setLong(3, player.id());
            ps.setInt(4, player.number());
            ps.setString(5, positionCode(player.position()));
            ps.setString(6, starter ? gridFor(players.indexOf(player)) : null);
            ps.setBoolean(7, starter);
        });

        return sum(results);
    }

    private int insertPlayerStats(Long matchRecordId, Long teamId, List<PlayerSeed> players, Random random) {
        String sql = """
                INSERT INTO player_fixture_stat
                (fixture_id, team_id, player_id, minutes_played, rating, is_captain, is_substitute,
                 goals, assists, conceded, saves, shots_total, shots_on_target, passes_total, passes_key,
                 pass_accuracy, tackles_total, blocks, interceptions, duels_total, duels_won,
                 dribbles_attempts, dribbles_success, dribbles_past, fouls_drawn, fouls_committed,
                 yellow_cards, red_cards, offsides, penalty_won, penalty_committed, penalty_scored,
                 penalty_missed, penalty_saved)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int[][] results = jdbcTemplate.batchUpdate(sql, players, players.size(), (ps, player) -> {
            int index = players.indexOf(player);
            boolean starter = index < 11;
            boolean goalkeeper = "Goalkeeper".equalsIgnoreCase(player.position());
            int shotsTotal = goalkeeper ? random.nextInt(2) : random.nextInt(6);
            int shotsOnTarget = random.nextInt(shotsTotal + 1);
            int passesTotal = random.nextInt(55) + 15;
            int dribblesAttempts = goalkeeper ? 0 : random.nextInt(6);
            int duelsTotal = random.nextInt(12) + 3;

            ps.setLong(1, matchRecordId);
            ps.setLong(2, teamId);
            ps.setLong(3, player.id());
            ps.setInt(4, starter ? 60 + random.nextInt(31) : random.nextInt(35));
            ps.setDouble(5, Math.round((5.8 + random.nextDouble() * 3.7) * 10) / 10.0);
            ps.setBoolean(6, index == 1);
            ps.setBoolean(7, !starter);
            ps.setInt(8, random.nextInt(100) > 88 ? 1 : 0);
            ps.setInt(9, random.nextInt(100) > 88 ? 1 : 0);
            ps.setInt(10, goalkeeper ? random.nextInt(4) : 0);
            ps.setInt(11, goalkeeper ? random.nextInt(7) : 0);
            ps.setInt(12, shotsTotal);
            ps.setInt(13, shotsOnTarget);
            ps.setInt(14, passesTotal);
            ps.setInt(15, random.nextInt(4));
            ps.setInt(16, 65 + random.nextInt(34));
            ps.setInt(17, random.nextInt(6));
            ps.setInt(18, random.nextInt(4));
            ps.setInt(19, random.nextInt(5));
            ps.setInt(20, duelsTotal);
            ps.setInt(21, random.nextInt(duelsTotal + 1));
            ps.setInt(22, dribblesAttempts);
            ps.setInt(23, random.nextInt(dribblesAttempts + 1));
            ps.setInt(24, random.nextInt(4));
            ps.setInt(25, random.nextInt(4));
            ps.setInt(26, random.nextInt(4));
            ps.setInt(27, random.nextInt(100) > 84 ? 1 : 0);
            ps.setInt(28, random.nextInt(100) > 97 ? 1 : 0);
            ps.setInt(29, random.nextInt(3));
            ps.setInt(30, random.nextInt(100) > 96 ? 1 : 0);
            ps.setInt(31, random.nextInt(100) > 96 ? 1 : 0);
            ps.setInt(32, random.nextInt(100) > 94 ? 1 : 0);
            ps.setInt(33, random.nextInt(100) > 95 ? 1 : 0);
            ps.setInt(34, goalkeeper && random.nextInt(100) > 94 ? 1 : 0);
        });

        return sum(results);
    }

    private String randomFormation(Random random) {
        String[] formations = {"4-3-3", "4-2-3-1", "3-4-3", "4-4-2", "3-5-2"};
        return formations[random.nextInt(formations.length)];
    }

    private String positionCode(String position) {
        if (position == null || position.isBlank()) {
            return "M";
        }
        return switch (position) {
            case "Goalkeeper" -> "G";
            case "Defender" -> "D";
            case "Midfielder" -> "M";
            case "Attacker" -> "F";
            default -> position.substring(0, 1).toUpperCase();
        };
    }

    private String gridFor(int index) {
        return switch (index) {
            case 0 -> "1:1";
            case 1, 2, 3, 4 -> "2:" + index;
            case 5, 6, 7 -> "3:" + (index - 4);
            case 8, 9, 10 -> "4:" + (index - 7);
            default -> null;
        };
    }

    private int sum(int[] results) {
        int sum = 0;
        for (int result : results) {
            sum += result;
        }
        return sum;
    }

    private int sum(int[][] results) {
        int sum = 0;
        for (int[] batch : results) {
            sum += sum(batch);
        }
        return sum;
    }

    private record TeamSeed(
            Long id,
            Long teamId,
            String name,
            String code,
            Long venueId,
            String venueName,
            String venueCity
    ) {
    }

    private record PlayerSeed(
            Long id,
            Long teamId,
            Integer number,
            String position
    ) {
    }

    public record BulkInsertResult(
            int matchCount,
            int lineupCount,
            int statCount,
            long elapsedMillis
    ) {
    }
}

