package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.FixtureResponseDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureService {

    private final FixtureRecordRepository fixtureRecordRepository;

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, int size) {
        List<Fixture> fixtures = fixtureRecordRepository.findRecentFixturesWithCursor(cursorId, season, size);

        boolean hasNext = false;
        if (fixtures.size() > size) {
            hasNext = true;
            fixtures.remove(fixtures.size() - 1);
        }

        List<FixtureResponseDto.Summary> content = fixtures.stream()
                .map(fixture -> FixtureResponseDto.Summary.builder()
                        .fixtureId(fixture.getFixtureId())
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
                        .build())
                .toList();

        Long nextCursor = hasNext ? fixtures.get(fixtures.size() - 1).getId() : null;

        return CursorResponse.<FixtureResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
