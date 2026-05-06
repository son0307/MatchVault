package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.TeamStanding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamStandingRepository extends JpaRepository<TeamStanding, Long> {

    @EntityGraph(attributePaths = "team")
    List<TeamStanding> findAllBySeasonOrderByRankAsc(Integer season);

    Optional<TeamStanding> findByTeamTeamIdAndSeason(Long teamId, Integer season);
}
