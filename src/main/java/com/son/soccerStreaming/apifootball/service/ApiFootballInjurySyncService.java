package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerAbsence;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.repository.PlayerAbsenceRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballInjurySyncService {

    private static final int CHUNK_SIZE = 100;
    private static final String DEFAULT_ABSENCE_TYPE = "Missing Fixture";
    private static final String DEFAULT_REASON = "Unknown";

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;

    public int syncInjuries(Integer league, Integer season) {
        List<ApiFootballInjuryDto.InjuryResponse> injuries = Optional.ofNullable(apiFootballClient.getInjuries(league, season))
                .orElse(List.of());
        int syncedCount = 0;

        List<List<ApiFootballInjuryDto.InjuryResponse>> chunks = chunks(injuries);
        for (int i = 0; i < chunks.size(); i++) {
            List<ApiFootballInjuryDto.InjuryResponse> chunk = chunks.get(i);
            log.info("API-Football injury chunk started. chunk={}/{}, size={}", i + 1, chunks.size(), chunk.size());
            syncedCount += syncInjuryChunk(chunk);
            log.info("API-Football injury chunk completed. chunk={}/{}, size={}", i + 1, chunks.size(), chunk.size());
        }

        log.info("API-Football injury sync completed. league={}, season={}, count={}", league, season, syncedCount);
        apiFootballSyncStatusService.recordSuccess("injuries", "Injuries", season);
        return syncedCount;
    }

    private int syncInjuryChunk(List<ApiFootballInjuryDto.InjuryResponse> injuries) {
        Integer count = transactionTemplate.execute(status -> {
            int syncedCount = 0;
            for (ApiFootballInjuryDto.InjuryResponse injury : injuries) {
                if (upsertInjury(injury)) {
                    syncedCount++;
                }
            }
            // Clear each injury chunk so bulk admin sync does not keep every absence managed until completion.
            entityManager.flush();
            entityManager.clear();
            return syncedCount;
        });
        return count != null ? count : 0;
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

        Optional<Fixture> fixture = fixtureRepository.findByFixtureId(fixtureInfo.getId());
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

    private List<List<ApiFootballInjuryDto.InjuryResponse>> chunks(List<ApiFootballInjuryDto.InjuryResponse> injuries) {
        List<List<ApiFootballInjuryDto.InjuryResponse>> chunks = new ArrayList<>();
        for (int i = 0; i < injuries.size(); i += CHUNK_SIZE) {
            chunks.add(injuries.subList(i, Math.min(i + CHUNK_SIZE, injuries.size())));
        }
        return chunks;
    }
}
