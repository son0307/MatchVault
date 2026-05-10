package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
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
@ConditionalOnProperty(name = "api-football.sync.fixtures.enabled", havingValue = "true")
public class ApiFootballFixtureSyncScheduler {

    private static final List<String> LIVE_CANDIDATE_STATUSES = List.of("SCHEDULED", "LIVE");

    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixtures.league:39}")
    private Integer league;

    @Value("${api-football.sync.fixtures.season:2024}")
    private Integer season;

    @Value("${api-football.sync.fixtures.live-window-before-minutes:10}")
    private Long liveWindowBeforeMinutes;

    @Value("${api-football.sync.fixtures.live-window-after-hours:4}")
    private Long liveWindowAfterHours;

    @Scheduled(cron = "${api-football.sync.fixtures.daily-cron:0 20 4 * * *}")
    public void syncSeasonFixturesDaily() {
        syncSeasonFixtures("daily");
    }

    @Scheduled(cron = "${api-football.sync.fixtures.live-cron:0 * * * * *}")
    public void syncLiveFixturesWhenMatchWindowOpen() {
        if (!hasFixtureInLiveWindow()) {
            return;
        }

        try {
            apiFootballFixtureSyncService.syncLiveFixtures(league, season);
        } catch (Exception e) {
            log.error("API-Football live fixture sync failed.", e);
        }
    }

    private void syncSeasonFixtures(String reason) {
        try {
            apiFootballFixtureSyncService.syncSeasonFixtures(league, season);
        } catch (Exception e) {
            log.error("API-Football season fixture sync failed. reason={}, league={}, season={}", reason, league, season, e);
        }
    }

    private boolean hasFixtureInLiveWindow() {
        LocalDateTime now = LocalDateTime.now();
        return fixtureRecordRepository.existsByFixtureDateBetweenAndFixtureStatusIn(
                now.minusHours(liveWindowAfterHours),
                now.plusMinutes(liveWindowBeforeMinutes),
                LIVE_CANDIDATE_STATUSES
        );
    }
}
