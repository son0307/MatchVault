package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.CursorResponse;
import com.son.soccerStreaming.fixture.dto.FixtureMetaResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
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

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final FixtureRepository fixtureRepository;

    @Transactional(readOnly = true)
    public FixtureResponseDto.Summary getFixture(Long fixtureId) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        return toSummary(fixture);
    }

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, Integer round,
                                                                         LocalDate date, int size) {
        return getRecentFixtures(cursorId, season, round, date, null, null, null, size);
    }

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, Integer round,
                                                                         LocalDate date, LocalDate dateFrom,
                                                                         LocalDate dateTo, Long teamId, int size) {
        DateRange dateRange = utcRangeForKoreaDates(date, dateFrom, dateTo);
        List<Fixture> fixtures = fixtureRepository.findRecentFixturesWithCursor(
                cursorId, season, round, dateRange.startDateTime(), dateRange.endDateTime(), teamId, size);

        boolean hasNext = false;
        if (fixtures.size() > size) {
            hasNext = true;
            fixtures.remove(fixtures.size() - 1);
        }

        List<FixtureResponseDto.Summary> content = fixtures.stream()
                .map(this::toSummary)
                .toList();

        Long nextCursor = hasNext ? fixtures.get(fixtures.size() - 1).getId() : null;

        return CursorResponse.<FixtureResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    @Transactional(readOnly = true)
    public FixtureMetaResponseDto getFixtureMeta(Integer season) {
        return FixtureMetaResponseDto.builder()
                .minDate(toKoreaDate(fixtureRepository.findMinFixtureDateBySeason(season).orElse(null)))
                .maxDate(toKoreaDate(fixtureRepository.findMaxFixtureDateBySeason(season).orElse(null)))
                .minRound(fixtureRepository.findMinRoundBySeason(season).orElse(null))
                .maxRound(fixtureRepository.findMaxRoundBySeason(season).orElse(null))
                .build();
    }

    private FixtureResponseDto.Summary toSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(fixture.getFixtureDate())
                .round(fixture.getRound())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeTeamLogoUrl(fixture.getHomeTeam().getLogoUrl())
                .awayTeamLogoUrl(fixture.getAwayTeam().getLogoUrl())
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

    private DateRange utcRangeForKoreaDates(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null || dateTo != null) {
            LocalDateTime startDateTime = dateFrom == null ? null : koreaStartAsUtc(dateFrom);
            LocalDateTime endDateTime = dateTo == null ? null : koreaStartAsUtc(dateTo.plusDays(1));
            return new DateRange(startDateTime, endDateTime);
        }

        if (date == null) {
            return new DateRange(null, null);
        }

        return new DateRange(koreaStartAsUtc(date), koreaStartAsUtc(date.plusDays(1)));
    }

    private LocalDateTime koreaStartAsUtc(LocalDate date) {
        return date.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private LocalDate toKoreaDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA_ZONE).toLocalDate();
    }

    private record DateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    }
}
