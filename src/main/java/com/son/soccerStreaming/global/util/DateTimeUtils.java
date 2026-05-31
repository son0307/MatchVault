package com.son.soccerStreaming.global.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class DateTimeUtils {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private DateTimeUtils() {
    }

    public static LocalDateTime utcToKorea(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }

        return utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toLocalDateTime();
    }
}
