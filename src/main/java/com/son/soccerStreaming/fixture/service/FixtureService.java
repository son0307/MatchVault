package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.CursorResponse;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureService {

    private final FixtureRepository fixtureRepository;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, Integer round,
                                                                         LocalDate date, int size) {
        DateRange dateRange = utcRangeForKoreaDate(date);
        List<Fixture> fixtures = fixtureRepository.findRecentFixturesWithCursor(
                cursorId, season, round, dateRange.startDateTime(), dateRange.endDateTime(), size);

        boolean hasNext = false;
        if (fixtures.size() > size) {
            hasNext = true;
            fixtures.remove(fixtures.size() - 1);
        }

        List<FixtureResponseDto.Summary> content = fixtures.stream()
                .map(fixture -> FixtureResponseDto.Summary.builder()
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

    private DateRange utcRangeForKoreaDate(LocalDate date) {
        if (date == null) {
            return new DateRange(null, null);
        }

        // API-Football 날짜는 UTC 기준으로 저장되므로, 선택한 한국 날짜를 UTC 조회 범위로 변환한다.
        LocalDateTime startDateTime = date.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime endDateTime = date.plusDays(1).atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        return new DateRange(startDateTime, endDateTime);
    }

    private record DateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    }
}
