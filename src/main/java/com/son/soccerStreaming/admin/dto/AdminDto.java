package com.son.soccerStreaming.admin.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public class AdminDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUpdateRequest {
        private String name;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private Long venueId;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerUpdateRequest {
        private String name;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private Integer height;
        private Integer weight;
        private String position;
        private Integer number;
        private String photoUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureUpdateRequest {
        private LocalDateTime fixtureDate;
        private String referee;
        private String timezone;
        private Long timestamp;
        private Long firstPeriod;
        private Long secondPeriod;
        private Integer round;
        private Integer season;
        private Long venueId;
        private String venueName;
        private String venueCity;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
        private String fixtureStatus;
        private Integer homeScore;
        private Integer awayScore;
        private Boolean homeWinner;
        private Boolean awayWinner;
        private Integer halftimeHomeScore;
        private Integer halftimeAwayScore;
        private Integer fulltimeHomeScore;
        private Integer fulltimeAwayScore;
        private Integer extratimeHomeScore;
        private Integer extratimeAwayScore;
        private Integer penaltyHomeScore;
        private Integer penaltyAwayScore;
        private String homeFormation;
        private String awayFormation;
        private String homeCoachName;
        private String awayCoachName;
        private String homePlayerColorPrimary;
        private String homePlayerColorNumber;
        private String homePlayerColorBorder;
        private String homeGoalkeeperColorPrimary;
        private String homeGoalkeeperColorNumber;
        private String homeGoalkeeperColorBorder;
        private String awayPlayerColorPrimary;
        private String awayPlayerColorNumber;
        private String awayPlayerColorBorder;
        private String awayGoalkeeperColorPrimary;
        private String awayGoalkeeperColorNumber;
        private String awayGoalkeeperColorBorder;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureEventUpdateRequest {
        private Integer elapsed;
        private Integer extra;
        private Long teamId;
        private Long playerId;
        private Long assistPlayerId;
        private String eventType;
        private String eventDetail;
        private String comments;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureLineupUpdateRequest {
        private Integer jerseyNumber;
        private String position;
        private String grid;
        private Boolean starter;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureTeamStatUpdateRequest {
        private Integer shotsOnGoal;
        private Integer shotsOffGoal;
        private Integer totalShots;
        private Integer blockedShots;
        private Integer shotsInsideBox;
        private Integer shotsOutsideBox;
        private Integer fouls;
        private Integer cornerKicks;
        private Integer offsides;
        private Integer ballPossession;
        private Integer yellowCards;
        private Integer redCards;
        private Integer goalkeeperSaves;
        private Integer totalPasses;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Double expectedGoals;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixturePlayerStatUpdateRequest {
        private Integer minutesPlayed;
        private Double rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer goals;
        private Integer assists;
        private Integer conceded;
        private Integer saves;
        private Integer shotsTotal;
        private Integer shotsOnTarget;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Integer tacklesTotal;
        private Integer blocks;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer dribblesPast;
        private Integer foulsDrawn;
        private Integer foulsCommitted;
        private Integer yellowCards;
        private Integer redCards;
        private Integer offsides;
        private Integer penaltyWon;
        private Integer penaltyCommitted;
        private Integer penaltyScored;
        private Integer penaltyMissed;
        private Integer penaltySaved;
    }

    @Getter
    @Builder
    public static class TeamAdminResponse {
        private Long teamId;
        private String name;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private Long venueId;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class PlayerAdminResponse {
        private Long playerId;
        private String name;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private Integer height;
        private Integer weight;
        private String position;
        private Integer number;
        private String photoUrl;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class FixtureAdminSummaryResponse {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private Integer season;
        private Integer round;
        private Long homeTeamId;
        private String homeTeamName;
        private Long awayTeamId;
        private String awayTeamName;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
    }

    @Getter
    @Builder
    public static class FixtureAdminDetailResponse {
        private FixtureAdminResponse fixture;
        private List<FixtureEventAdminResponse> events;
        private List<FixtureLineupAdminResponse> lineups;
        private List<FixtureTeamStatAdminResponse> teamStats;
        private List<FixturePlayerStatAdminResponse> playerStats;
    }

    @Getter
    @Builder
    public static class FixtureAdminResponse {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String referee;
        private String timezone;
        private Long timestamp;
        private Long firstPeriod;
        private Long secondPeriod;
        private Integer round;
        private Integer season;
        private Long venueId;
        private String venueName;
        private String venueCity;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
        private String fixtureStatus;
        private Integer homeScore;
        private Integer awayScore;
        private Boolean homeWinner;
        private Boolean awayWinner;
        private Integer halftimeHomeScore;
        private Integer halftimeAwayScore;
        private Integer fulltimeHomeScore;
        private Integer fulltimeAwayScore;
        private Integer extratimeHomeScore;
        private Integer extratimeAwayScore;
        private Integer penaltyHomeScore;
        private Integer penaltyAwayScore;
        private Long homeTeamId;
        private String homeTeamName;
        private Long awayTeamId;
        private String awayTeamName;
        private String homeFormation;
        private String awayFormation;
        private String homeCoachName;
        private String awayCoachName;
        private String homePlayerColorPrimary;
        private String homePlayerColorNumber;
        private String homePlayerColorBorder;
        private String homeGoalkeeperColorPrimary;
        private String homeGoalkeeperColorNumber;
        private String homeGoalkeeperColorBorder;
        private String awayPlayerColorPrimary;
        private String awayPlayerColorNumber;
        private String awayPlayerColorBorder;
        private String awayGoalkeeperColorPrimary;
        private String awayGoalkeeperColorNumber;
        private String awayGoalkeeperColorBorder;
    }

    @Getter
    @Builder
    public static class FixtureEventAdminResponse {
        private Integer eventSequence;
        private Integer elapsed;
        private Integer extra;
        private Long teamId;
        private String teamName;
        private Long playerId;
        private String playerName;
        private Long assistPlayerId;
        private String assistPlayerName;
        private String eventType;
        private String eventDetail;
        private String comments;
    }

    @Getter
    @Builder
    public static class FixtureLineupAdminResponse {
        private Long teamId;
        private String teamName;
        private Long playerId;
        private String playerName;
        private Integer jerseyNumber;
        private String position;
        private String grid;
        private boolean starter;
    }

    @Getter
    @Builder
    public static class FixtureTeamStatAdminResponse {
        private Long teamId;
        private String teamName;
        private Integer shotsOnGoal;
        private Integer shotsOffGoal;
        private Integer totalShots;
        private Integer blockedShots;
        private Integer shotsInsideBox;
        private Integer shotsOutsideBox;
        private Integer fouls;
        private Integer cornerKicks;
        private Integer offsides;
        private Integer ballPossession;
        private Integer yellowCards;
        private Integer redCards;
        private Integer goalkeeperSaves;
        private Integer totalPasses;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Double expectedGoals;
    }

    @Getter
    @Builder
    public static class FixturePlayerStatAdminResponse {
        private Long playerId;
        private String playerName;
        private Long teamId;
        private String teamName;
        private Integer minutesPlayed;
        private Double rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer goals;
        private Integer assists;
        private Integer conceded;
        private Integer saves;
        private Integer shotsTotal;
        private Integer shotsOnTarget;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Integer tacklesTotal;
        private Integer blocks;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer dribblesPast;
        private Integer foulsDrawn;
        private Integer foulsCommitted;
        private Integer yellowCards;
        private Integer redCards;
        private Integer offsides;
        private Integer penaltyWon;
        private Integer penaltyCommitted;
        private Integer penaltyScored;
        private Integer penaltyMissed;
        private Integer penaltySaved;
    }

    @Getter
    @Builder
    public static class ManualOverrideResponse {
        private String fieldName;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class SyncResponse {
        private String task;
        private boolean success;
        private boolean queued;
        private int count;
        private String message;
    }

    @Getter
    @Builder
    public static class AuditLogResponse {
        private Long id;
        private String adminEmail;
        private String type;
        private String targetType;
        private Long targetId;
        private String message;
        private String details;
        private boolean success;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuditLogListResponse {
        private List<AuditLogResponse> logs;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class SyncStatusResponse {
        private List<SyncStatusItem> statuses;
    }

    @Getter
    @Builder
    public static class SyncStatusItem {
        private String task;
        private String label;
        private OffsetDateTime lastSyncedAt;
    }
}
