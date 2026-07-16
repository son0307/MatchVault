package com.son.soccerStreaming.news.dto;

import java.time.Instant;
import java.util.List;

public record TeamNewsListResponseDto(
        Instant lastCollectedAt,
        List<TeamNewsResponseDto> articles
) {
}
