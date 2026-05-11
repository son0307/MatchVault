package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.TeamRepository;
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
@ConditionalOnProperty(name = "api-football.sync.players.squads.run-on-startup", havingValue = "true")
public class ApiFootballPlayerSquadStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final TeamRepository teamRepository;

    @Value("${api-football.sync.players.squads.startup-delay-ms:7000}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        log.info("API-Football startup squad sync started.");
        for (Team team : teamRepository.findAllByOrderByNameAsc()) {
            try {
                apiFootballPlayerSyncService.syncSquad(team.getTeamId());
            } catch (Exception e) {
                log.error("API-Football squad sync failed. teamId={}", team.getTeamId(), e);
            }
        }
    }
}
