package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballStandingLocalUpdateService {

    private static final String LIVE_IMPACT_KEY = "standing:live-impact:%d:%d";
    private static final String LIVE_IMPACT_PATTERN = "standing:live-impact:%d:*";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api-football.sync.standings.local-finished-update-enabled:false}")
    private boolean localFinishedUpdateEnabled;

    @Value("${api-football.sync.standings.local-live-update-enabled:false}")
    private boolean localLiveUpdateEnabled;

    @Value("${api-football.sync.standings.season:2025}")
    private Integer season;

    @Value("${api-football.sync.standings.local-live-impact-ttl-hours:6}")
    private Long liveImpactTtlHours;

    public void applyFixtureState(Fixture fixture) {
        if (!localUpdatesEnabled() || fixture.getHomeScore() == null || fixture.getAwayScore() == null) {
            return;
        }

        if (!isStandingImpactStatus(fixture.getStatusShort())) {
            deleteImpact(fixture.getFixtureId(), season);
            return;
        }

        saveImpact(new LiveStandingImpact(
                fixture.getFixtureId(),
                season,
                fixture.getHomeTeam().getTeamId(),
                fixture.getAwayTeam().getTeamId(),
                fixture.getHomeScore(),
                fixture.getAwayScore(),
                fixture.getStatusShort(),
                LocalDateTime.now()
        ));

        log.debug("Standing live impact cached. fixtureId={}, season={}, status={}, score={}-{}",
                fixture.getFixtureId(), season, fixture.getStatusShort(), fixture.getHomeScore(), fixture.getAwayScore());
    }

    public List<LiveStandingImpact> findImpacts(Integer season) {
        Set<String> keys = redisTemplate.keys(LIVE_IMPACT_PATTERN.formatted(season));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<LiveStandingImpact> impacts = new ArrayList<>();
        for (String key : keys) {
            String impactJson = redisTemplate.opsForValue().get(key);
            readImpact(key, impactJson).ifPresent(impacts::add);
        }
        return impacts;
    }

    private boolean localUpdatesEnabled() {
        return localLiveUpdateEnabled || localFinishedUpdateEnabled;
    }

    private boolean isStandingImpactStatus(String statusShort) {
        return isLive(statusShort) || isFinished(statusShort);
    }

    private boolean isLive(String statusShort) {
        return "1H".equals(statusShort)
                || "HT".equals(statusShort)
                || "2H".equals(statusShort)
                || "ET".equals(statusShort)
                || "BT".equals(statusShort)
                || "P".equals(statusShort)
                || "SUSP".equals(statusShort)
                || "INT".equals(statusShort)
                || "LIVE".equals(statusShort);
    }

    private boolean isFinished(String statusShort) {
        return "FT".equals(statusShort) || "AET".equals(statusShort) || "PEN".equals(statusShort);
    }

    private Optional<LiveStandingImpact> findImpact(Long fixtureId, Integer season) {
        String impactJson = redisTemplate.opsForValue().get(liveImpactKey(fixtureId, season));
        return readImpact(liveImpactKey(fixtureId, season), impactJson);
    }

    private Optional<LiveStandingImpact> readImpact(String key, String impactJson) {
        if (impactJson == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(impactJson, LiveStandingImpact.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize Redis standing live impact. key={}", key, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private void saveImpact(LiveStandingImpact impact) {
        try {
            String impactJson = objectMapper.writeValueAsString(impact);
            redisTemplate.opsForValue().set(
                    liveImpactKey(impact.getFixtureId(), impact.getSeason()),
                    impactJson,
                    Duration.ofHours(liveImpactTtlHours)
            );
        } catch (JacksonException e) {
            log.error("Failed to serialize Redis standing live impact. fixtureId={}, season={}",
                    impact.getFixtureId(), impact.getSeason(), e);
        }
    }

    private void deleteImpact(Long fixtureId, Integer season) {
        redisTemplate.delete(liveImpactKey(fixtureId, season));
    }

    private String liveImpactKey(Long fixtureId, Integer season) {
        return LIVE_IMPACT_KEY.formatted(season, fixtureId);
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor
    public static class LiveStandingImpact {
        private Long fixtureId;
        private Integer season;
        private Long homeTeamId;
        private Long awayTeamId;
        private Integer homeScore;
        private Integer awayScore;
        private String statusShort;
        private LocalDateTime appliedAt;
    }
}
