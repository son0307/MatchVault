package com.son.soccerStreaming.team.repository;

import com.son.soccerStreaming.team.entity.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = "venue")
    Optional<Team> findByTeamId(Long teamId);

    List<Team> findAllByOrderByNameAsc();

    List<Team> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    @Query("""
            select t.id
            from Team t
            where t.logoUrl is not null
              and t.logoUrl <> ''
              and t.logoObjectKey is null
              and (t.logoCacheFailedAt is null or t.logoCacheFailedAt < :retryBefore)
            order by t.name asc
            """)
    List<Long> findLogoCacheCandidateIds(LocalDateTime retryBefore, Pageable pageable);
}
