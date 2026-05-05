package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.FixtureStatResponseDto;
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
public class FixtureStatService {

    private final FixtureRecordRepository fixtureRecordRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final FixutreTeamStatAggregator fixutreTeamStatAggregator;

    @Transactional(readOnly = true)
    public FixtureStatResponseDto getFixtureStats(Long fixtureId) {
        Fixture fixture = fixtureRecordRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<PlayerFixtureStat> allStats = playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId);

        return FixtureStatResponseDto.builder()
                .fixtureId(fixtureId)
                .homeTeamStat(fixutreTeamStatAggregator.aggregate(
                        fixture.getHomeTeam().getTeamId(),
                        valueOf(fixture.getHomeScore()),
                        allStats
                ))
                .awayTeamStat(fixutreTeamStatAggregator.aggregate(
                        fixture.getAwayTeam().getTeamId(),
                        valueOf(fixture.getAwayScore()),
                        allStats
                ))
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
