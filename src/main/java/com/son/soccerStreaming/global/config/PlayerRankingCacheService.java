package com.son.soccerStreaming.global.config;

import com.son.soccerStreaming.league.dto.LeaguePlayerRankingResponseDto;
import com.son.soccerStreaming.league.service.LeaguePlayerRankingService;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import com.son.soccerStreaming.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Checks Redis before entering the transactional ranking services. */
@Service
@RequiredArgsConstructor
public class PlayerRankingCacheService {

    private final LeaguePlayerRankingService leaguePlayerRankingService;
    private final TeamService teamService;

    @Cacheable(cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
            key = "'v8:league:' + #leagueId + ':season:' + #season", sync = true)
    public LeaguePlayerRankingResponseDto getLeagueRankings(Integer leagueId, Integer season) {
        return leaguePlayerRankingService.getRankings(leagueId, season);
    }

    @Cacheable(cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
            key = "'v4:team:' + #teamId + ':season:' + #season", sync = true)
    public TeamResponseDto.PlayerRankings getTeamRankings(Long teamId, Integer season) {
        return teamService.getPlayerRankings(teamId, season);
    }
}
