package com.son.soccerStreaming.news.dto;

import java.time.Instant;

public record TeamNewsResponseDto(
        Long articleId,
        String originalTitle,
        String translatedTitle,
        String publisherName,
        String originalUrl,
        Instant publishedAt
) {
}
