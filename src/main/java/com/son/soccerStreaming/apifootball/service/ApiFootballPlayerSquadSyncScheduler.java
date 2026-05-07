package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.players.squads.enabled", havingValue = "true")
public class ApiFootballPlayerSquadSyncScheduler {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final TeamRepository teamRepository;

    @Value("${api-football.sync.players.squads.delay-ms:7000}")
    private Long delayMs;

    @Scheduled(cron = "${api-football.sync.players.squads.cron:0 50 4 * * SUN}")
    public void syncSquadsWeekly() {
        for (Team team : teamRepository.findAll()) {
            try {
                apiFootballPlayerSyncService.syncSquad(team.getTeamId());
                sleepBetweenRequests();
            } catch (Exception e) {
                log.error("API-Football squad sync failed. teamId={}", team.getTeamId(), e);
            }
        }
    }

    private void sleepBetweenRequests() {
        if (delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
