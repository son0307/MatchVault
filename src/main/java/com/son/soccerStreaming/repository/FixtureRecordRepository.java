package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FixtureRecordRepository extends JpaRepository<Fixture, Long>, FixtureRecordRepositoryCustom {
    Optional<Fixture> findByFixtureId(Long fixtureId);

    boolean existsByFixtureStatus(String fixtureStatus);

    boolean existsByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    List<Fixture> findAllByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    List<Fixture> findAllByFixtureStatus(String fixtureStatus);

    List<Fixture> findAllByFixtureStatusNot(String fixtureStatus);
}
