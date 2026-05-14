package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.PlayerFixtureStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerFixtureStatRepository extends JpaRepository<PlayerFixtureStat, Long> {

    List<PlayerFixtureStat> findAllByFixtureFixtureId(Long fixtureId);

    List<PlayerFixtureStat> findAllByPlayerPlayerIdOrderByFixtureFixtureDateDesc(Long playerId);

    Optional<PlayerFixtureStat> findByFixtureFixtureIdAndPlayerPlayerId(Long fixtureId, Long playerId);

    @Query("SELECT s.player.playerId as playerId, s.team.teamId as teamId " +
            "FROM PlayerFixtureStat s " +
            "WHERE s.player.playerId IN :playerIds " +
            "AND s.fixture.season = :season " +
            "AND s.fixture.fixtureDate = (" +
            "    SELECT MAX(latest.fixture.fixtureDate) " +
            "    FROM PlayerFixtureStat latest " +
            "    WHERE latest.player.playerId = s.player.playerId " +
            "    AND latest.fixture.season = :season" +
            ")")
    List<LatestPlayerTeam> findLatestTeamsByPlayerIdsAndSeason(
            @Param("playerIds") List<Long> playerIds,
            @Param("season") Integer season
    );

    @Query("SELECT " +
            "COUNT(s) as totalFixtures, " +
            "COALESCE(SUM(s.minutesPlayed), 0) as minutesPlayed, " +
            "COALESCE(AVG(s.rating), 0) as averageRating, " +
            "COALESCE(SUM(s.goals), 0) as goals, " +
            "COALESCE(SUM(s.assists), 0) as assists, " +
            "COALESCE(SUM(s.conceded), 0) as conceded, " +
            "COALESCE(SUM(s.saves), 0) as saves, " +
            "COALESCE(SUM(s.shotsTotal), 0) as shotsTotal, " +
            "COALESCE(SUM(s.shotsOnTarget), 0) as shotsOnTarget, " +
            "COALESCE(SUM(s.passesTotal), 0) as passesTotal, " +
            "COALESCE(SUM(s.passesKey), 0) as passesKey, " +
            "COALESCE(AVG(s.passAccuracy), 0) as avgPassAccuracy, " +
            "COALESCE(SUM(s.foulsDrawn), 0) as foulsDrawn, " +
            "COALESCE(SUM(s.foulsCommitted), 0) as foulsCommitted, " +
            "COALESCE(SUM(s.tacklesTotal), 0) as tacklesTotal, " +
            "COALESCE(SUM(s.blocks), 0) as blocks, " +
            "COALESCE(SUM(s.interceptions), 0) as interceptions, " +
            "COALESCE(SUM(s.duelsTotal), 0) as duelsTotal, " +
            "COALESCE(SUM(s.duelsWon), 0) as duelsWon, " +
            "COALESCE(SUM(s.dribblesAttempts), 0) as dribblesAttempts, " +
            "COALESCE(SUM(s.dribblesSuccess), 0) as dribblesSuccess, " +
            "COALESCE(SUM(s.dribblesPast), 0) as dribblesPast, " +
            "COALESCE(SUM(s.yellowCards), 0) as yellowCards, " +
            "COALESCE(SUM(s.redCards), 0) as redCards, " +
            "COALESCE(SUM(s.offsides), 0) as offsides, " +
            "COALESCE(SUM(s.penaltyWon), 0) as penaltyWon, " +
            "COALESCE(SUM(s.penaltyCommitted), 0) as penaltyCommitted, " +
            "COALESCE(SUM(s.penaltyScored), 0) as penaltyScored, " +
            "COALESCE(SUM(s.penaltyMissed), 0) as penaltyMissed, " +
            "COALESCE(SUM(s.penaltySaved), 0) as penaltySaved " +
            "FROM PlayerFixtureStat s " +
            "WHERE s.player.playerId = :apiPlayerId")
    SeasonStatSummary findSeasonStatSummaryByPlayerId(@Param("apiPlayerId") Long apiPlayerId);

    interface SeasonStatSummary {
        long getTotalFixtures();
        int getMinutesPlayed();
        double getAverageRating();
        int getGoals();
        int getAssists();
        int getConceded();
        int getSaves();
        int getShotsTotal();
        int getShotsOnTarget();
        int getPassesTotal();
        int getPassesKey();
        double getAvgPassAccuracy();
        int getFoulsDrawn();
        int getFoulsCommitted();
        int getTacklesTotal();
        int getBlocks();
        int getInterceptions();
        int getDuelsTotal();
        int getDuelsWon();
        int getDribblesAttempts();
        int getDribblesSuccess();
        int getDribblesPast();
        int getYellowCards();
        int getRedCards();
        int getOffsides();
        int getPenaltyWon();
        int getPenaltyCommitted();
        int getPenaltyScored();
        int getPenaltyMissed();
        int getPenaltySaved();
    }

    interface LatestPlayerTeam {
        Long getPlayerId();
        Long getTeamId();
    }
}
