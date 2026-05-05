package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.LiveFixtureSnapshotDto;
import com.son.soccerStreaming.dto.FixtureEventDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.PlayerFixtureStat;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.PlayerFixtureStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveFixtureSnapshotService {

    private final FixtureRecordRepository fixtureRecordRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final FixutreTeamStatAggregator fixutreTeamStatAggregator;
    private final FixtureRedisService fixtureRedisService;

    @Transactional(readOnly = true)
    public LiveFixtureSnapshotDto rebuildAndCacheSnapshot(Long fixtureId, FixtureEventDto latestEvent) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<PlayerFixtureStat> playerStats = playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId);

        LiveFixtureSnapshotDto snapshot = LiveFixtureSnapshotDto.builder()
                .fixtureId(fixtureId)
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .fixtureStatus(fixture.getFixtureStatus())
                .elapsed(fixture.getElapsed())
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
                .homeTeamStat(fixutreTeamStatAggregator.aggregate(
                        fixture.getHomeTeam().getTeamId(),
                        valueOf(fixture.getHomeScore()),
                        playerStats
                ))
                .awayTeamStat(fixutreTeamStatAggregator.aggregate(
                        fixture.getAwayTeam().getTeamId(),
                        valueOf(fixture.getAwayScore()),
                        playerStats
                ))
                .latestEvent(latestEvent)
                .build();

        fixtureRedisService.saveLiveSnapshot(snapshot);
        return snapshot;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
