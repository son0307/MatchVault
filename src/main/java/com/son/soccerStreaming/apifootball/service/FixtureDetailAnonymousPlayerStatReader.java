package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Reads fixture-only statistics for provider players without a usable player ID. */
@Service
@RequiredArgsConstructor
public class FixtureDetailAnonymousPlayerStatReader {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<AnonymousPlayerStat> findForFixture(long generationId, long fixtureId) {
        List<String> payloads = jdbcTemplate.queryForList("""
                SELECT payload FROM fixture_detail_generation_response
                WHERE generation_id = ? AND fixture_id = ?
                """, String.class, generationId, fixtureId);
        if (payloads.isEmpty()) {
            return List.of();
        }
        try {
            ApiFootballLiveDto.FixtureResponse response = objectMapper.readValue(
                    payloads.get(0), ApiFootballLiveDto.FixtureResponse.class);
            if (response.getPlayers() == null) {
                return List.of();
            }
            List<AnonymousPlayerStat> result = new ArrayList<>();
            for (ApiFootballLiveDto.FixturePlayersResponse team : response.getPlayers()) {
                if (team == null || team.getTeam() == null || team.getTeam().getId() == null
                        || team.getPlayers() == null) {
                    continue;
                }
                for (ApiFootballLiveDto.PlayerStatResponse player : team.getPlayers()) {
                    if (player == null || player.getPlayer() == null || player.getPlayer().getId() == null
                            || player.getPlayer().getId() > 0 || player.getPlayer().getName() == null
                            || player.getPlayer().getName().isBlank() || player.getStatistics() == null
                            || player.getStatistics().isEmpty() || player.getStatistics().get(0) == null) {
                        continue;
                    }
                    result.add(new AnonymousPlayerStat(team.getTeam().getId(),
                            toPlayerStat(player.getPlayer().getName(), player.getStatistics().get(0))));
                }
            }
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Published fixture response could not be read: " + fixtureId, exception);
        }
    }

    private FixturePlayerStatResponseDto.PlayerStat toPlayerStat(
            String name, ApiFootballLiveDto.PlayerStatistics stat) {
        var games = stat.getGames();
        var shots = stat.getShots();
        var goals = stat.getGoals();
        var passes = stat.getPasses();
        var tackles = stat.getTackles();
        var duels = stat.getDuels();
        var dribbles = stat.getDribbles();
        var fouls = stat.getFouls();
        var cards = stat.getCards();
        var penalty = stat.getPenalty();
        int totalPasses = zero(passes != null ? passes.getTotal() : null);
        int accuratePasses = zero(passes != null ? parseInteger(passes.getAccuracy()) : null);
        return FixturePlayerStatResponseDto.PlayerStat.builder()
                .playerName(name.trim())
                .jerseyNumber(games != null ? games.getNumber() : null)
                .position(games != null ? games.getPosition() : null)
                .minutesPlayed(zero(games != null ? games.getMinutes() : null))
                .rating(parseDouble(games != null ? games.getRating() : null))
                .captain(games != null ? games.getCaptain() : null)
                .substitute(games != null ? games.getSubstitute() : null)
                .goals(zero(goals != null ? goals.getTotal() : null))
                .assists(zero(goals != null ? goals.getAssists() : null))
                .conceded(zero(goals != null ? goals.getConceded() : null))
                .saves(zero(goals != null ? goals.getSaves() : null))
                .shotsTotal(zero(shots != null ? shots.getTotal() : null))
                .shotsOnTarget(zero(shots != null ? shots.getOn() : null))
                .passesTotal(totalPasses)
                .passesKey(zero(passes != null ? passes.getKey() : null))
                .passesAccurate(accuratePasses)
                .passAccuracy(totalPasses > 0 ? (int) Math.round(accuratePasses * 100.0 / totalPasses) : 0)
                .tacklesTotal(zero(tackles != null ? tackles.getTotal() : null))
                .blocks(zero(tackles != null ? tackles.getBlocks() : null))
                .interceptions(zero(tackles != null ? tackles.getInterceptions() : null))
                .duelsTotal(zero(duels != null ? duels.getTotal() : null))
                .duelsWon(zero(duels != null ? duels.getWon() : null))
                .dribblesAttempts(zero(dribbles != null ? dribbles.getAttempts() : null))
                .dribblesSuccess(zero(dribbles != null ? dribbles.getSuccess() : null))
                .dribblesPast(zero(dribbles != null ? dribbles.getPast() : null))
                .foulsDrawn(zero(fouls != null ? fouls.getDrawn() : null))
                .foulsCommitted(zero(fouls != null ? fouls.getCommitted() : null))
                .yellowCards(zero(cards != null ? cards.getYellow() : null))
                .redCards(zero(cards != null ? cards.getRed() : null))
                .offsides(zero(stat.getOffsides()))
                .penaltyWon(zero(penalty != null ? penalty.getWon() : null))
                .penaltyCommitted(zero(penalty != null ? penalty.getCommited() : null))
                .penaltyScored(zero(penalty != null ? penalty.getScored() : null))
                .penaltyMissed(zero(penalty != null ? penalty.getMissed() : null))
                .penaltySaved(zero(penalty != null ? penalty.getSaved() : null))
                .build();
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        return digits.isBlank() ? null : Integer.parseInt(digits);
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank() || value.equals("–")) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    public record AnonymousPlayerStat(Long teamId, FixturePlayerStatResponseDto.PlayerStat stat) {
    }
}
