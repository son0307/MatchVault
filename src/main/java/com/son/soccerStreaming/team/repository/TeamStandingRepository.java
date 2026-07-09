package com.son.soccerStreaming.team.repository;

import com.son.soccerStreaming.team.entity.TeamStanding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamStandingRepository extends JpaRepository<TeamStanding, Long> {

    @EntityGraph(attributePaths = "team")
    List<TeamStanding> findAllByLeagueIdAndSeason(Integer leagueId, Integer season);

    boolean existsByLeagueIdAndSeason(Integer leagueId, Integer season);

    Optional<TeamStanding> findByTeamTeamIdAndLeagueIdAndSeason(Long teamId, Integer leagueId, Integer season);
}
