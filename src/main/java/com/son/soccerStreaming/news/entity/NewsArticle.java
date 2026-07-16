package com.son.soccerStreaming.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "news_article", indexes = {
        @Index(name = "idx_news_article_published_at", columnList = "published_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String urlHash;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, length = 1000)
    private String originalTitle;

    @Column(length = 1000)
    private String translatedTitle;

    // Null means the article predates one-time automatic translation tracking.
    // Such legacy articles are translated only through an explicit administrator request.
    private Boolean autoTranslationAttempted;

    // Kept only so installations created by the earlier schema can still insert rows.
    // Translation selection and UI behavior no longer depend on these legacy columns.
    @Column(name = "translation_status", nullable = false, length = 20)
    @Builder.Default
    private String legacyTranslationStatus = "UNTRACKED";

    @Column(name = "translation_attempt_count", nullable = false)
    @Builder.Default
    private int legacyTranslationAttemptCount = 0;

    @Column(nullable = false)
    private String publisherName;

    @Column(nullable = false)
    private String publisherDomain;

    private Instant publishedAt;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public void updateMetadata(String originalUrl, String originalTitle, String publisherName,
                               String publisherDomain, Instant publishedAt, Instant seenAt) {
        this.originalUrl = originalUrl;
        this.publisherName = publisherName;
        this.publisherDomain = publisherDomain;
        this.publishedAt = publishedAt;
        this.lastSeenAt = seenAt;
        if (!Objects.equals(this.originalTitle, originalTitle)) {
            this.originalTitle = originalTitle;
            this.translatedTitle = null;
        }
    }

    public void markTranslated(String translatedTitle) {
        this.translatedTitle = translatedTitle;
    }

    public void markAutoTranslationAttempted() {
        this.autoTranslationAttempted = true;
    }
}
