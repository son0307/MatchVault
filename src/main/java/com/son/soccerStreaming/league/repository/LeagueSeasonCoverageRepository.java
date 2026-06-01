package com.son.soccerStreaming.league.repository;

import com.son.soccerStreaming.league.entity.LeagueSeasonCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueSeasonCoverageRepository extends JpaRepository<LeagueSeasonCoverage, Long> {

    Optional<LeagueSeasonCoverage> findByLeagueIdAndSeasonYear(Integer leagueId, Integer seasonYear);

    List<LeagueSeasonCoverage> findByLeagueIdOrderBySeasonYearDesc(Integer leagueId);

    Optional<LeagueSeasonCoverage> findFirstByLeagueIdAndCurrentSeasonTrue(Integer leagueId);

    Optional<LeagueSeasonCoverage> findFirstByLeagueIdOrderBySeasonYearDesc(Integer leagueId);
}
