package com.son.soccerStreaming.favorite.service;

import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.favorite.entity.FavoritePlayer;
import com.son.soccerStreaming.favorite.entity.FavoriteTeam;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.util.DateTimeUtils;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FavoriteTeamRepository favoriteTeamRepository;
    private final FavoritePlayerRepository favoritePlayerRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;

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
                .map(favorite -> toTeamCard(favorite.getTeam(), season))
                .toList();

        List<FavoriteDashboardResponseDto.PlayerCard> players = favoritePlayerRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(favorite -> toPlayerCard(favorite.getPlayer()))
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

    private FavoriteDashboardResponseDto.TeamCard toTeamCard(Team team, Integer season) {
        TeamStanding standing = teamStandingRepository.findByTeamTeamIdAndSeason(team.getTeamId(), season)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<FavoriteDashboardResponseDto.TeamFixture> recentFixtures = fixtureRepository
                .findRecentByTeam(team.getTeamId(), season, now, PageRequest.of(0, 5))
                .stream()
                .map(fixture -> toTeamFixture(fixture, team))
                .toList();
        FavoriteDashboardResponseDto.TeamFixture nextFixture = fixtureRepository
                .findNextByTeam(team.getTeamId(), season, now, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(fixture -> toTeamFixture(fixture, team))
                .orElse(null);
        FavoriteDashboardResponseDto.LiveTeamFixture liveFixture = fixtureRepository
                .findLiveByTeam(team.getTeamId(), season, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toLiveTeamFixture)
                .orElse(null);

        return FavoriteDashboardResponseDto.TeamCard.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .logoUrl(team.getLogoUrl())
                .rank(standing != null ? standing.getRank() : null)
                .points(standing != null ? standing.getPoints() : null)
                .form(standing != null ? standing.getForm() : null)
                .recentFixtures(recentFixtures)
                .nextFixture(nextFixture)
                .liveFixture(liveFixture)
                .build();
    }

    private FavoriteDashboardResponseDto.TeamFixture toTeamFixture(Fixture fixture, Team favoriteTeam) {
        return FavoriteDashboardResponseDto.TeamFixture.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .result(resultOf(fixture, favoriteTeam))
                .build();
    }

    private FavoriteDashboardResponseDto.LiveTeamFixture toLiveTeamFixture(Fixture fixture) {
        return FavoriteDashboardResponseDto.LiveTeamFixture.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .elapsed(fixture.getElapsed())
                .build();
    }

    private FavoriteDashboardResponseDto.PlayerCard toPlayerCard(Player player) {
        PlayerFixtureStat recentMatch = playerFixtureStatRepository
                .findTop5ByPlayerPlayerIdOrderByFixtureFixtureDateDesc(player.getPlayerId())
                .stream()
                .findFirst()
                .orElse(null);
        PlayerTeamSeasonStat seasonStat = playerTeamSeasonStatRepository
                .findTopByPlayerPlayerIdOrderBySeasonDesc(player.getPlayerId())
                .orElse(null);

        return FavoriteDashboardResponseDto.PlayerCard.builder()
                .playerId(player.getPlayerId())
                .playerName(player.getName())
                .photoUrl(player.getPhotoUrl())
                .position(player.getPosition())
                .recentMatch(recentMatch != null ? toRecentPlayerMatch(recentMatch) : null)
                .seasonStat(seasonStat != null ? toPlayerSeasonStat(seasonStat) : null)
                .build();
    }

    private FavoriteDashboardResponseDto.RecentPlayerMatch toRecentPlayerMatch(PlayerFixtureStat stat) {
        Fixture fixture = stat.getFixture();
        Team team = stat.getTeam();
        Team opponent = opponentOf(fixture, team);

        return FavoriteDashboardResponseDto.RecentPlayerMatch.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .teamName(team.getName())
                .opponentTeamName(opponent != null ? opponent.getName() : null)
                .teamScore(scoreOf(fixture, team))
                .opponentScore(opponent != null ? scoreOf(fixture, opponent) : null)
                .minutesPlayed(stat.getMinutesPlayed())
                .rating(stat.getRating())
                .goals(stat.getGoals())
                .assists(stat.getAssists())
                .build();
    }

    private FavoriteDashboardResponseDto.PlayerSeasonStat toPlayerSeasonStat(PlayerTeamSeasonStat stat) {
        return FavoriteDashboardResponseDto.PlayerSeasonStat.builder()
                .season(stat.getSeason())
                .teamName(stat.getTeam().getName())
                .teamLogoUrl(stat.getTeam().getLogoUrl())
                .appearances(stat.getAppearances())
                .minutes(stat.getMinutes())
                .rating(stat.getRating())
                .goals(stat.getGoals())
                .assists(stat.getAssists())
                .yellowCards(stat.getYellowCards())
                .redCards(stat.getRedCards())
                .build();
    }

    private String resultOf(Fixture fixture, Team team) {
        Integer teamScore = scoreOf(fixture, team);
        Team opponent = opponentOf(fixture, team);
        Integer opponentScore = opponent != null ? scoreOf(fixture, opponent) : null;

        if (teamScore == null || opponentScore == null) {
            return "-";
        }
        if (teamScore > opponentScore) {
            return "W";
        }
        if (teamScore < opponentScore) {
            return "L";
        }
        return "D";
    }

    private Team opponentOf(Fixture fixture, Team team) {
        if (fixture.getHomeTeam().getId().equals(team.getId())) {
            return fixture.getAwayTeam();
        }
        if (fixture.getAwayTeam().getId().equals(team.getId())) {
            return fixture.getHomeTeam();
        }
        return null;
    }

    private Integer scoreOf(Fixture fixture, Team team) {
        if (fixture.getHomeTeam().getId().equals(team.getId())) {
            return fixture.getHomeScore();
        }
        if (fixture.getAwayTeam().getId().equals(team.getId())) {
            return fixture.getAwayScore();
        }
        return null;
    }
}
