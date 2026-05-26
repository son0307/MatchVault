package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FixtureRepository extends JpaRepository<Fixture, Long>, FixtureRepositoryCustom {
    Optional<Fixture> findByFixtureId(Long fixtureId);

    List<Fixture> findAllBySeasonOrderByFixtureDateAsc(Integer season);

    boolean existsByFixtureStatus(String fixtureStatus);

    boolean existsByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Fixture> findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
            Integer season,
            LocalDateTime start,
            LocalDateTime end
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Fixture> findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(Integer season, String fixtureStatus);

    List<Fixture> findAllByFixtureStatus(String fixtureStatus);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureDate <= :now " +
            "ORDER BY f.fixtureDate DESC")
    List<Fixture> findRecentByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureDate > :now " +
            "ORDER BY f.fixtureDate ASC")
    List<Fixture> findNextByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureStatus = 'LIVE' " +
            "ORDER BY f.fixtureDate ASC")
    List<Fixture> findLiveByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            org.springframework.data.domain.Pageable pageable
    );
}
