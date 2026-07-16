package com.son.soccerStreaming.news.entity;

import com.son.soccerStreaming.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "team_news_article",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_news_article_team_article",
                columnNames = {"team_id", "article_id"}
        ),
        indexes = {
                @Index(name = "idx_team_news_article_team_seen", columnList = "team_id, last_seen_at"),
                @Index(name = "idx_team_news_article_article", columnList = "article_id")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(name = "result_position")
    private Integer resultPosition;

    public void markSeen(Instant seenAt, int resultPosition) {
        this.lastSeenAt = seenAt;
        this.resultPosition = resultPosition;
    }
}
