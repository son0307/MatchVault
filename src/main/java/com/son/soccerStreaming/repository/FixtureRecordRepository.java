package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FixtureRecordRepository extends JpaRepository<Fixture, Long>, FixtureRecordRepositoryCustom {
    Optional<Fixture> findByFixtureId(Long fixtureId);

    List<Fixture> findAllByOrderByFixtureDateAsc();

    List<Fixture> findAllBySeasonOrderByFixtureDateAsc(Integer season);

    boolean existsByFixtureStatus(String fixtureStatus);

    boolean existsByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    List<Fixture> findAllByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    List<Fixture> findAllByFixtureStatus(String fixtureStatus);

    List<Fixture> findAllByFixtureStatusNot(String fixtureStatus);

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
}
