package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FixtureRecordRepository extends JpaRepository<Fixture, Long>, FixtureRecordRepositoryCustom {
    Optional<Fixture> findByFixtureId(Long fixtureId);

    boolean existsByFixtureStatus(String fixtureStatus);
}
