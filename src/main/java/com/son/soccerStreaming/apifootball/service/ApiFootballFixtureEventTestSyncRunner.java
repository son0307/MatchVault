package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-events.test-runner-enabled", havingValue = "true")
public class ApiFootballFixtureEventTestSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;

    @Value("${api-football.sync.fixture-events.test-fixture-id-from:1208021}")
    private Long fixtureIdFrom;

    @Value("${api-football.sync.fixture-events.test-fixture-id-to:1208030}")
    private Long fixtureIdTo;

    @Override
    public void run(String... args) {
        log.info("Sync fixture events starting up...");
        for (long fixtureId = fixtureIdFrom; fixtureId <= fixtureIdTo; fixtureId++) {
            try {
                apiFootballFixtureEventSyncService.syncEvents(fixtureId);
            } catch (Exception e) {
                log.error("API-Football fixture event test sync failed. fixtureId={}", fixtureId, e);
            }
        }
    }
}
