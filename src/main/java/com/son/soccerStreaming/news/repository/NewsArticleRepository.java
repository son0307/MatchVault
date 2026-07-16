package com.son.soccerStreaming.news.repository;

import com.son.soccerStreaming.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Optional<NewsArticle> findByUrlHash(String urlHash);

    @Query("""
            select n
            from NewsArticle n
            where n.translatedTitle is null
              and n.autoTranslationAttempted = false
            order by n.firstSeenAt asc, n.id asc
            """)
    List<NewsArticle> findTranslationCandidates();

    @Query("""
            select distinct n
            from TeamNewsArticle t
            join t.article n
            where t.team.teamId = :teamId
              and n.translatedTitle is null
              and n.autoTranslationAttempted = false
            order by n.firstSeenAt asc, n.id asc
            """)
    List<NewsArticle> findTranslationCandidatesByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("""
            delete from NewsArticle n
            where not exists (
                select t.id from TeamNewsArticle t where t.article = n
            )
            """)
    int deleteOrphans();
}
