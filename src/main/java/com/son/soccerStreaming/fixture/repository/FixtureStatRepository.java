package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.FixtureStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FixtureStatRepository extends JpaRepository<FixtureStat, Long> {

    List<FixtureStat> findAllByFixtureFixtureId(Long fixtureId);

    Optional<FixtureStat> findByFixtureFixtureIdAndTeamTeamId(Long fixtureId, Long teamId);

    @Query("""
            SELECT fs.team.teamId AS teamId,
                   AVG(fs.ballPossession) AS averagePossession,
                   SUM(COALESCE(fs.yellowCards, 0)) AS yellowCards,
                   SUM(COALESCE(fs.redCards, 0)) AS redCards
            FROM FixtureStat fs
            WHERE fs.fixture.leagueId = :leagueId
              AND fs.fixture.season = :season
            GROUP BY fs.team.teamId
            """)
    List<TeamSeasonStatAggregate> findTeamSeasonStatAggregates(
            @Param("leagueId") Integer leagueId,
            @Param("season") Integer season
    );

    interface TeamSeasonStatAggregate {
        Long getTeamId();
        Double getAveragePossession();
        Long getYellowCards();
        Long getRedCards();
    }
}
