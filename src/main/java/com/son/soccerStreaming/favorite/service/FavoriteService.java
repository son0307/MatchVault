package com.son.soccerStreaming.favorite.service;

import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.favorite.entity.FavoritePlayer;
import com.son.soccerStreaming.favorite.entity.FavoriteTeam;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FavoriteTeamRepository favoriteTeamRepository;
    private final FavoritePlayerRepository favoritePlayerRepository;
    private final FavoriteCardService favoriteCardService;

    @Transactional
    public FavoriteDashboardResponseDto addTeam(Long userId, Long teamId, Integer season) {
        if (!favoriteTeamRepository.existsByUserIdAndTeamTeamId(userId, teamId)) {
            AppUser user = findUser(userId);
            Team team = teamRepository.findByTeamId(teamId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
            favoriteTeamRepository.save(FavoriteTeam.of(user, team));
        }
        return getDashboard(userId, season);
    }

    @Transactional
    public FavoriteDashboardResponseDto removeTeam(Long userId, Long teamId, Integer season) {
        favoriteTeamRepository.deleteByUserIdAndTeamTeamId(userId, teamId);
        return getDashboard(userId, season);
    }

    @Transactional
    public FavoriteDashboardResponseDto addPlayer(Long userId, Long playerId, Integer season) {
        if (!favoritePlayerRepository.existsByUserIdAndPlayerPlayerId(userId, playerId)) {
            AppUser user = findUser(userId);
            Player player = playerRepository.findByPlayerId(playerId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
            favoritePlayerRepository.save(FavoritePlayer.of(user, player));
        }
        return getDashboard(userId, season);
    }

    @Transactional
    public FavoriteDashboardResponseDto removePlayer(Long userId, Long playerId, Integer season) {
        favoritePlayerRepository.deleteByUserIdAndPlayerPlayerId(userId, playerId);
        return getDashboard(userId, season);
    }

    @Transactional(readOnly = true)
    public FavoriteDashboardResponseDto getDashboard(Long userId, Integer season) {
        List<FavoriteDashboardResponseDto.TeamCard> teams = favoriteTeamRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(favorite -> favoriteCardService.getTeamCard(favorite.getTeam().getTeamId(), season))
                .toList();

        List<FavoriteDashboardResponseDto.PlayerCard> players = favoritePlayerRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(favorite -> favoriteCardService.getPlayerCard(favorite.getPlayer().getPlayerId(), season))
                .toList();

        return FavoriteDashboardResponseDto.builder()
                .teams(teams)
                .players(players)
                .build();
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
