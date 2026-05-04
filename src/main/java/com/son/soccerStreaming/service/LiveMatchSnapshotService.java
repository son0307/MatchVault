package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.LiveMatchSnapshotDto;
import com.son.soccerStreaming.dto.MatchEventDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.entity.PlayerMatchStat;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveMatchSnapshotService {

    private final MatchRecordRepository matchRecordRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final MatchTeamStatAggregator matchTeamStatAggregator;
    private final MatchRedisService matchRedisService;

    @Transactional(readOnly = true)
    public LiveMatchSnapshotDto rebuildAndCacheSnapshot(Long fixtureId, MatchEventDto latestEvent) {
        MatchRecord matchRecord = matchRecordRepository.findByApiFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        List<PlayerMatchStat> playerStats = playerMatchStatRepository.findAllByMatchRecordApiFixtureId(fixtureId);

        LiveMatchSnapshotDto snapshot = LiveMatchSnapshotDto.builder()
                .fixtureId(fixtureId)
                .statusShort(matchRecord.getStatusShort())
                .statusLong(matchRecord.getStatusLong())
                .matchCategory(matchRecord.getMatchCategory())
                .elapsed(matchRecord.getElapsed())
                .homeTeamStat(matchTeamStatAggregator.aggregate(
                        matchRecord.getHomeTeam().getTeamApiId(),
                        valueOf(matchRecord.getHomeScore()),
                        playerStats
                ))
                .awayTeamStat(matchTeamStatAggregator.aggregate(
                        matchRecord.getAwayTeam().getTeamApiId(),
                        valueOf(matchRecord.getAwayScore()),
                        playerStats
                ))
                .latestEvent(latestEvent)
                .build();

        matchRedisService.saveLiveSnapshot(snapshot);
        return snapshot;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
