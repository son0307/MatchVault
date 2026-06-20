package com.son.soccerStreaming.league.service;

import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.league.dto.LeagueTeamRankingResponseDto;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeagueTeamRankingService {

    private static final int TOP_LIMIT = 20;

    private final TeamStandingRepository teamStandingRepository;
    private final FixtureStatRepository fixtureStatRepository;
    private final MediaUrlService mediaUrlService;

    @Cacheable(
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            key = "'v1:league:' + #leagueId + ':season:' + #season",
            sync = true
    )
    public LeagueTeamRankingResponseDto getRankings(Integer leagueId, Integer season) {
        List<TeamStanding> standings = teamStandingRepository.findAllBySeason(season);
        if (standings.isEmpty()) {
            return emptyResponse(leagueId, season);
        }

        Map<Long, FixtureStatRepository.TeamSeasonStatAggregate> statAggregates =
                fixtureStatRepository.findTeamSeasonStatAggregates(season).stream()
                        .collect(Collectors.toMap(
                                FixtureStatRepository.TeamSeasonStatAggregate::getTeamId,
                                aggregate -> aggregate
                        ));

        List<LeagueTeamRankingResponseDto.Row> rows = standings.stream()
                .map(standing -> toRow(standing, statAggregates.get(standing.getTeam().getTeamId())))
                .toList();

        return LeagueTeamRankingResponseDto.builder()
                .leagueId(leagueId)
                .season(season)
                .goalsFor(rank(rows, row -> true, goalsForComparator()))
                .goalsAgainst(rank(rows, row -> true, goalsAgainstComparator()))
                .possession(rank(rows, row -> row.getAveragePossession() != null, possessionComparator()))
                .yellowCards(rank(rows, row -> true, yellowCardsComparator()))
                .redCards(rank(rows, row -> true, redCardsComparator()))
                .build();
    }

    private LeagueTeamRankingResponseDto.Row toRow(
            TeamStanding standing,
            FixtureStatRepository.TeamSeasonStatAggregate aggregate
    ) {
        int played = valueOf(standing.getPlayed());
        int goalsFor = valueOf(standing.getGoalsFor());
        int goalsAgainst = valueOf(standing.getGoalsAgainst());

        return LeagueTeamRankingResponseDto.Row.builder()
                .teamId(standing.getTeam().getTeamId())
                .teamName(standing.getTeam().getName())
                .teamLogoUrl(mediaUrlService.teamLogoUrl(standing.getTeam()))
                .teamRank(standing.getRank())
                .played(played)
                .goalsFor(goalsFor)
                .goalsAgainst(goalsAgainst)
                .goalsForPerMatch(perMatch(goalsFor, played))
                .goalsAgainstPerMatch(perMatch(goalsAgainst, played))
                .averagePossession(aggregate != null ? roundToTwoDecimals(aggregate.getAveragePossession()) : null)
                .yellowCards(aggregate != null ? longValue(aggregate.getYellowCards()) : 0)
                .redCards(aggregate != null ? longValue(aggregate.getRedCards()) : 0)
                .build();
    }

    private List<LeagueTeamRankingResponseDto.Row> rank(
            List<LeagueTeamRankingResponseDto.Row> rows,
            Predicate<LeagueTeamRankingResponseDto.Row> filter,
            Comparator<LeagueTeamRankingResponseDto.Row> comparator
    ) {
        List<LeagueTeamRankingResponseDto.Row> ranked = rows.stream()
                .filter(filter)
                .sorted(comparator)
                .limit(TOP_LIMIT)
                .toList();
        List<LeagueTeamRankingResponseDto.Row> result = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            result.add(ranked.get(index).toBuilder().rank(index + 1).build());
        }
        return result;
    }

    private Comparator<LeagueTeamRankingResponseDto.Row> goalsForComparator() {
        return Comparator.comparingInt(LeagueTeamRankingResponseDto.Row::getGoalsFor).reversed()
                .thenComparingInt(this::teamRank)
                .thenComparingLong(LeagueTeamRankingResponseDto.Row::getTeamId);
    }

    private Comparator<LeagueTeamRankingResponseDto.Row> goalsAgainstComparator() {
        return Comparator.comparingInt(LeagueTeamRankingResponseDto.Row::getGoalsAgainst)
                .thenComparingInt(this::teamRank)
                .thenComparingLong(LeagueTeamRankingResponseDto.Row::getTeamId);
    }

    private Comparator<LeagueTeamRankingResponseDto.Row> possessionComparator() {
        return Comparator.comparing(
                        LeagueTeamRankingResponseDto.Row::getAveragePossession,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparingInt(this::teamRank)
                .thenComparingLong(LeagueTeamRankingResponseDto.Row::getTeamId);
    }

    private Comparator<LeagueTeamRankingResponseDto.Row> yellowCardsComparator() {
        return Comparator.comparingInt(LeagueTeamRankingResponseDto.Row::getYellowCards).reversed()
                .thenComparingInt(this::teamRank)
                .thenComparingLong(LeagueTeamRankingResponseDto.Row::getTeamId);
    }

    private Comparator<LeagueTeamRankingResponseDto.Row> redCardsComparator() {
        return Comparator.comparingInt(LeagueTeamRankingResponseDto.Row::getRedCards).reversed()
                .thenComparingInt(this::teamRank)
                .thenComparingLong(LeagueTeamRankingResponseDto.Row::getTeamId);
    }

    private int teamRank(LeagueTeamRankingResponseDto.Row row) {
        return row.getTeamRank() != null ? row.getTeamRank() : Integer.MAX_VALUE;
    }

    private double perMatch(int total, int played) {
        return played > 0 ? roundToTwoDecimals((double) total / played) : 0.0;
    }

    private Double roundToTwoDecimals(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private int valueOf(Integer value) {
        return value != null ? value : 0;
    }

    private int longValue(Long value) {
        return value != null ? Math.toIntExact(value) : 0;
    }

    private LeagueTeamRankingResponseDto emptyResponse(Integer leagueId, Integer season) {
        return LeagueTeamRankingResponseDto.builder()
                .leagueId(leagueId)
                .season(season)
                .goalsFor(List.of())
                .goalsAgainst(List.of())
                .possession(List.of())
                .yellowCards(List.of())
                .redCards(List.of())
                .build();
    }
}
