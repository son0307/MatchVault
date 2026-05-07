package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballPlayerDto;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.PlayerRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballPlayerSyncService {

    private final ApiFootballClient apiFootballClient;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    @Value("${api-football.sync.players.profile-fallback-enabled:false}")
    private boolean profileFallbackEnabled;

    @Transactional
    public int syncSquad(Long teamId) {
        Optional<Team> team = teamRepository.findByTeamId(teamId);
        if (team.isEmpty()) {
            log.warn("Skip squad sync because team does not exist. teamId={}", teamId);
            return 0;
        }

        List<ApiFootballPlayerDto.SquadResponse> responses = apiFootballClient.getPlayerSquad(teamId);
        int syncedCount = 0;
        for (ApiFootballPlayerDto.SquadResponse response : responses) {
            if (response.getPlayers() == null) {
                continue;
            }
            for (ApiFootballPlayerDto.SquadPlayer playerInfo : response.getPlayers()) {
                if (playerInfo.getId() == null || playerInfo.getName() == null || playerInfo.getName().isBlank()) {
                    continue;
                }
                upsertSquadPlayer(playerInfo, team.get());
                syncedCount++;
            }
        }

        log.info("API-Football squad sync completed. teamId={}, count={}", teamId, syncedCount);
        return syncedCount;
    }

    @Transactional
    public Optional<Player> findOrFetchPlayer(Long playerId, String fallbackName, Team team,
                                              Integer defaultNumber, String position, String photoUrl) {
        if (playerId == null) {
            return Optional.empty();
        }

        Optional<Player> existing = playerRepository.findByPlayerId(playerId);
        if (existing.isPresent()) {
            return existing;
        }

        if (profileFallbackEnabled) {
            Optional<Player> fetched = fetchProfile(playerId, team, defaultNumber, position);
            if (fetched.isPresent()) {
                return fetched;
            }
        }

        if (fallbackName == null || fallbackName.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(saveMinimalPlayer(playerId, fallbackName, team, defaultNumber, position, photoUrl));
    }

    @Transactional
    public Optional<Player> fetchProfile(Long playerId, Team team, Integer defaultNumber, String position) {
        return apiFootballClient.getPlayerProfiles(playerId).stream()
                .map(ApiFootballPlayerDto.ProfileResponse::getPlayer)
                .filter(player -> player != null && player.getId() != null)
                .findFirst()
                .map(player -> upsertProfilePlayer(player, team, defaultNumber, position));
    }

    private Player upsertSquadPlayer(ApiFootballPlayerDto.SquadPlayer playerInfo, Team team) {
        Player player = playerRepository.findByPlayerId(playerInfo.getId())
                .orElseGet(() -> Player.builder()
                        .playerId(playerInfo.getId())
                        .name(playerInfo.getName())
                        .build());

        player.updateProfile(
                playerInfo.getName(),
                player.getFirstname(),
                player.getLastname(),
                playerInfo.getAge(),
                player.getBirthDate(),
                player.getBirthPlace(),
                player.getBirthCountry(),
                player.getNationality(),
                player.getHeight(),
                player.getWeight(),
                playerInfo.getPosition(),
                playerInfo.getNumber(),
                playerInfo.getPhoto()
        );
        player.updateTeam(team);
        return playerRepository.save(player);
    }

    private Player upsertProfilePlayer(ApiFootballPlayerDto.ProfilePlayer playerInfo, Team team,
                                       Integer defaultNumber, String position) {
        Player player = playerRepository.findByPlayerId(playerInfo.getId())
                .orElseGet(() -> Player.builder()
                        .playerId(playerInfo.getId())
                        .name(nameOrFallback(playerInfo.getName(), playerInfo.getId()))
                        .build());

        ApiFootballPlayerDto.Birth birth = playerInfo.getBirth();
        player.updateProfile(
                nameOrFallback(playerInfo.getName(), player.getName()),
                playerInfo.getFirstname(),
                playerInfo.getLastname(),
                playerInfo.getAge(),
                birth != null ? parseBirthDate(birth.getDate()) : null,
                birth != null ? birth.getPlace() : null,
                birth != null ? birth.getCountry() : null,
                playerInfo.getNationality(),
                playerInfo.getHeight(),
                playerInfo.getWeight(),
                position != null ? position : player.getPosition(),
                defaultNumber != null ? defaultNumber : player.getDefaultNumber(),
                playerInfo.getPhoto()
        );
        if (team != null) {
            player.updateTeam(team);
        }
        return playerRepository.save(player);
    }

    private Player saveMinimalPlayer(Long playerId, String name, Team team, Integer defaultNumber,
                                     String position, String photoUrl) {
        Player player = Player.builder()
                .playerId(playerId)
                .name(name)
                .defaultNumber(defaultNumber)
                .position(position)
                .photoUrl(photoUrl)
                .team(team)
                .build();
        return playerRepository.save(player);
    }

    private String nameOrFallback(String name, Long playerId) {
        return name != null && !name.isBlank() ? name : "Player " + playerId;
    }

    private String nameOrFallback(String name, String fallback) {
        return name != null && !name.isBlank() ? name : fallback;
    }

    private LocalDate parseBirthDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse API-Football player birth date. date={}", date);
            return null;
        }
    }
}
