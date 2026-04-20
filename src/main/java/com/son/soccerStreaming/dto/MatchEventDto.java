package com.son.soccerStreaming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchEventDto {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("match_id")
    private String matchId;

    private Long timestamp;

    @JsonProperty("match_minute")
    private String matchMinute;

    @JsonProperty("team_id")
    private String teamId;

    @JsonProperty("player_id")
    private String playerId;

    @JsonProperty("event_type")
    private String eventType;

    private Coordinates coordinates;

    @JsonProperty("event_detail")
    private Map<String, Object> eventDetail;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinates {
        private Double x;
        private Double y;
    }
}
