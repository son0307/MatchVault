package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveFixtureQueryService {

    private static final String LIVE_STATUS = "LIVE";

    private final FixtureRecordRepository fixtureRecordRepository;

    @Transactional(readOnly = true)
    public List<FixtureResponseDto.Summary> getTodayLiveFixtures(Integer season) {
        return fixtureRecordRepository
                .findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(season, LIVE_STATUS)
                .stream()
                .map(this::toFixtureSummary)
                .toList();
    }

    private FixtureResponseDto.Summary toFixtureSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(fixture.getFixtureDate())
                .round(fixture.getRound())
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeScore(valueOf(fixture.getHomeScore()))
                .awayScore(valueOf(fixture.getAwayScore()))
                .homeWinner(fixture.getHomeWinner())
                .awayWinner(fixture.getAwayWinner())
                .halftimeHomeScore(fixture.getHalftimeHomeScore())
                .halftimeAwayScore(fixture.getHalftimeAwayScore())
                .fulltimeHomeScore(fixture.getFulltimeHomeScore())
                .fulltimeAwayScore(fixture.getFulltimeAwayScore())
                .extratimeHomeScore(fixture.getExtratimeHomeScore())
                .extratimeAwayScore(fixture.getExtratimeAwayScore())
                .penaltyHomeScore(fixture.getPenaltyHomeScore())
                .penaltyAwayScore(fixture.getPenaltyAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
