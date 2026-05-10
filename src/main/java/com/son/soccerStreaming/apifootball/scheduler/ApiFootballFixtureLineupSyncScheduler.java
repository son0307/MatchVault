package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureLineupSyncService;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-lineups.enabled", havingValue = "true")
public class ApiFootballFixtureLineupSyncScheduler {

    private static final List<String> LINEUP_WINDOW_STATUSES = List.of("SCHEDULED", "LIVE");

    private final ApiFootballFixtureLineupSyncService apiFootballFixtureLineupSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixture-lineups.window-before-minutes:45}")
    private Long windowBeforeMinutes;

    @Value("${api-football.sync.fixture-lineups.window-after-hours:4}")
    private Long windowAfterHours;

    @Scheduled(cron = "${api-football.sync.fixture-lineups.live-cron:0 */15 * * * *}")
    public void syncLineupsInMatchWindow() {
        LocalDateTime now = LocalDateTime.now();
        List<Fixture> fixtures = fixtureRecordRepository.findAllByFixtureDateBetweenAndFixtureStatusIn(
                now.minusHours(windowAfterHours),
                now.plusMinutes(windowBeforeMinutes),
                LINEUP_WINDOW_STATUSES
        );
        apiFootballFixtureLineupSyncService.syncLineups(fixtures);
    }

    @Scheduled(cron = "${api-football.sync.fixture-lineups.daily-cron:0 40 4 * * *}")
    public void syncNonLiveLineupsDaily() {
        apiFootballFixtureLineupSyncService.syncLineups(fixtureRecordRepository.findAllByFixtureStatusNot("LIVE"));
    }
}
