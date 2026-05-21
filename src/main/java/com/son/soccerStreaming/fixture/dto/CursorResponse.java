package com.son.soccerStreaming.fixture.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CursorResponse<T> {
    private final List<T> content;
    private final Long nextCursor;
    private final boolean hasNext;
}