package com.son.soccerStreaming.team.repository;

import com.son.soccerStreaming.team.entity.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = "venue")
    Optional<Team> findByTeamId(Long teamId);

    List<Team> findAllByOrderByNameAsc();

    @Query("""
            select t
            from Team t
            where lower(t.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(t.koreanName, '')) like lower(concat('%', :keyword, '%'))
            order by t.name asc
            """)
    List<Team> findTop20ByNameOrKoreanNameContainingIgnoreCaseOrderByNameAsc(@Param("keyword") String keyword);

    @Query("""
            select t
            from Team t
            where exists (
                select f.id
                from Fixture f
                where f.season = :season
                  and (f.homeTeam = t or f.awayTeam = t)
            )
            order by t.name asc
            """)
    List<Team> findAllWithFixtureInSeasonOrderByNameAsc(@Param("season") Integer season);

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

    @Query("select t.adminLogoObjectKey from Team t where t.adminLogoObjectKey is not null")
    List<String> findAllAdminLogoObjectKeys();
}
