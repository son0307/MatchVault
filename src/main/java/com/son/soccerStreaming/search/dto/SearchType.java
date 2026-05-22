package com.son.soccerStreaming.search.dto;

import java.util.Locale;

public enum SearchType {
    ALL,
    TEAM,
    PLAYER,
    FIXTURE;

    public static SearchType from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return SearchType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ALL;
        }
    }
}
