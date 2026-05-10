package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.PlayerAbsence;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import com.son.soccerStreaming.repository.PlayerAbsenceRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballInjurySyncService {

    private static final String DEFAULT_ABSENCE_TYPE = "Missing Fixture";
    private static final String DEFAULT_REASON = "Unknown";

    private final ApiFootballClient apiFootballClient;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final TeamRepository teamRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;

    @Transactional
    public int syncInjuries(Integer league, Integer season) {
        List<ApiFootballInjuryDto.InjuryResponse> injuries = apiFootballClient.getInjuries(league, season);
        int syncedCount = 0;

        for (ApiFootballInjuryDto.InjuryResponse injury : injuries) {
            if (upsertInjury(injury)) {
                syncedCount++;
            }
        }

        log.info("API-Football injury sync completed. league={}, season={}, count={}", league, season, syncedCount);
        return syncedCount;
    }

    private boolean upsertInjury(ApiFootballInjuryDto.InjuryResponse injury) {
        ApiFootballInjuryDto.FixtureInfo fixtureInfo = injury.getFixture();
        ApiFootballInjuryDto.TeamInfo teamInfo = injury.getTeam();
        ApiFootballInjuryDto.PlayerInfo playerInfo = injury.getPlayer();

        if (fixtureInfo == null || fixtureInfo.getId() == null
                || teamInfo == null || teamInfo.getId() == null
                || playerInfo == null || playerInfo.getId() == null) {
            return false;
        }

        Optional<Fixture> fixture = fixtureRecordRepository.findByFixtureId(fixtureInfo.getId());
        Optional<Team> team = teamRepository.findByTeamId(teamInfo.getId());
        if (fixture.isEmpty() || team.isEmpty()) {
            log.warn("Skip injury sync because fixture or team does not exist. fixtureId={}, teamId={}",
                    fixtureInfo.getId(), teamInfo.getId());
            return false;
        }

        Optional<Player> player = apiFootballPlayerSyncService.findOrFetchPlayer(
                playerInfo.getId(),
                playerInfo.getName(),
                team.get(),
                null,
                null,
                playerInfo.getPhoto()
        );
        if (player.isEmpty()) {
            log.warn("Skip injury sync because player does not exist. fixtureId={}, playerId={}",
                    fixtureInfo.getId(), playerInfo.getId());
            return false;
        }

        PlayerAbsence absence = playerAbsenceRepository
                .findByPlayerPlayerIdAndFixtureFixtureId(player.get().getPlayerId(), fixture.get().getFixtureId())
                .orElseGet(() -> PlayerAbsence.builder()
                        .fixture(fixture.get())
                        .team(team.get())
                        .player(player.get())
                        .build());

        absence.updateAbsence(
                valueOrDefault(playerInfo.getType(), DEFAULT_ABSENCE_TYPE),
                valueOrDefault(playerInfo.getReason(), DEFAULT_REASON)
        );
        playerAbsenceRepository.save(absence);
        return true;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
