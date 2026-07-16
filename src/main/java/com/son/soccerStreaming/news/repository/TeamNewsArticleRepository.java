package com.son.soccerStreaming.news.repository;

import com.son.soccerStreaming.news.entity.NewsArticle;
import com.son.soccerStreaming.news.entity.TeamNewsArticle;
import com.son.soccerStreaming.team.entity.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TeamNewsArticleRepository extends JpaRepository<TeamNewsArticle, Long> {

    Optional<TeamNewsArticle> findByTeamAndArticle(Team team, NewsArticle article);

    @Query("""
            select t
            from TeamNewsArticle t
            join fetch t.article n
            where t.team.teamId = :teamId
              and n.id = :articleId
            """)
    Optional<TeamNewsArticle> findByTeamIdAndArticleId(
            @Param("teamId") Long teamId,
            @Param("articleId") Long articleId
    );

    @Query("""
            select t
            from TeamNewsArticle t
            join fetch t.article n
            where t.team.teamId = :teamId
              and t.lastSeenAt = (
                  select max(latestRelation.lastSeenAt)
                  from TeamNewsArticle latestRelation
                  where latestRelation.team.teamId = :teamId
              )
            order by
              case when t.resultPosition is null then 1 else 0 end,
              t.resultPosition asc,
              t.id asc
            """)
    List<TeamNewsArticle> findLatestByTeamId(@Param("teamId") Long teamId, Pageable pageable);

    int deleteByLastSeenAtBefore(Instant cutoff);
}
