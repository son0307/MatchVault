package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds the same column-level fixture overlay for public reads and projection. */
@Repository
@RequiredArgsConstructor
public class FixtureDetailFixtureFieldStore {

    private final JdbcTemplate jdbcTemplate;
    private volatile List<String> columns;

    private List<String> columns() {
        List<String> current = columns;
        if (current == null) {
            current = jdbcTemplate.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'fixture'
                    ORDER BY ordinal_position
                    """, String.class);
            if (current.isEmpty() || current.stream().anyMatch(column -> !column.matches("[A-Za-z0-9_]+"))) {
                throw new IllegalStateException("Fixture columns are missing or invalid");
            }
            columns = current;
        }
        return current;
    }

    /** f, live and season_lock must be joined by the caller. */
    public String effectiveColumns() {
        return columns().stream().map(column -> {
            String quoted = "`" + column + "`";
            if (List.of("id", "fixture_id", "season", "version").contains(column)) {
                return "f." + quoted + " AS " + quoted;
            }
            String adminField = adminField(column);
            return "CASE WHEN EXISTS (SELECT 1 FROM fixture_detail_fixture_field_change change_row "
                    + "WHERE change_row.season = f.season AND change_row.fixture_id = f.fixture_id "
                    + "AND change_row.field_name = '" + column + "' "
                    + "AND change_row.revision > season_lock.projected_fixture_revision) "
                    + "OR EXISTS (SELECT 1 FROM admin_field_override admin_change "
                    + "WHERE admin_change.target_type = 'FIXTURE' AND admin_change.target_id = f.fixture_id "
                    + "AND admin_change.field_name = '" + adminField + "') "
                    + "THEN live." + quoted + " ELSE f." + quoted + " END AS " + quoted;
        }).collect(Collectors.joining(", ")) + ", f.generation_id";
    }

    public void mergeIntoGeneration(long generationId, long fixtureId, long afterRevision) {
        String setters = columns().stream()
                .filter(column -> !List.of("id", "fixture_id", "season", "version").contains(column))
                .map(column -> {
                    String quoted = "`" + column + "`";
                    String adminField = adminField(column);
                    return "target." + quoted + " = CASE WHEN EXISTS ("
                            + "SELECT 1 FROM fixture_detail_fixture_field_change change_row "
                            + "WHERE change_row.season = target.season AND change_row.fixture_id = target.fixture_id "
                            + "AND change_row.field_name = '" + column + "' AND change_row.revision > "
                            + afterRevision + ") OR EXISTS (SELECT 1 FROM admin_field_override admin_change "
                            + "WHERE admin_change.target_type = 'FIXTURE' AND admin_change.target_id = target.fixture_id "
                            + "AND admin_change.field_name = '" + adminField + "') "
                            + "THEN live." + quoted + " ELSE target." + quoted + " END";
                }).collect(Collectors.joining(", "));
        jdbcTemplate.update("UPDATE fixture_generation target JOIN fixture live ON live.id = target.id "
                + "SET " + setters + " WHERE target.generation_id = ? AND target.fixture_id = ?",
                generationId, fixtureId);
    }

    private String adminField(String column) {
        if (column.equals("timestamp")) {
            return "fixtureDate";
        }
        String[] parts = column.split("_");
        StringBuilder camel = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length; index++) {
            camel.append(parts[index].substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(parts[index].substring(1));
        }
        return camel.toString();
    }
}
