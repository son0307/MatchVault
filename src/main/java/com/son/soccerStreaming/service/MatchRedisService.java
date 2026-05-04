package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.LiveMatchSnapshotDto;
import com.son.soccerStreaming.dto.MatchEventDto;
import com.son.soccerStreaming.dto.MatchStatResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRedisService {

    private static final Duration LIVE_CACHE_TTL = Duration.ofMinutes(10);
    private static final String LATEST_EVENT_KEY = "match:%d:latest_event";
    private static final String LIVE_SNAPSHOT_KEY = "match:%d:live_snapshot";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveLatestEvent(MatchEventDto event) {
        if (event.getFixtureId() == null) {
            log.warn("fixtureId가 없는 이벤트는 Redis 최신 이벤트로 저장하지 않습니다. event={}", event);
            return;
        }

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            redisTemplate.opsForValue().set(latestEventKey(event.getFixtureId()), eventJson, LIVE_CACHE_TTL);
        } catch (JacksonException e) {
            log.error("Redis 최신 이벤트 저장 중 JSON 변환 오류", e);
        }
    }

    public void saveLiveSnapshot(LiveMatchSnapshotDto snapshot) {
        if (snapshot.getFixtureId() == null) {
            log.warn("fixtureId가 없는 live snapshot은 Redis에 저장하지 않습니다.");
            return;
        }

        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(liveSnapshotKey(snapshot.getFixtureId()), snapshotJson, LIVE_CACHE_TTL);
        } catch (JacksonException e) {
            log.error("Redis live snapshot 저장 중 JSON 변환 오류", e);
        }
    }

    public Optional<LiveMatchSnapshotDto> getLiveSnapshot(Long fixtureId) {
        String snapshotJson = redisTemplate.opsForValue().get(liveSnapshotKey(fixtureId));
        if (snapshotJson == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(snapshotJson, LiveMatchSnapshotDto.class));
        } catch (JacksonException e) {
            log.error("Redis live snapshot 조회 중 JSON 변환 오류. fixtureId={}", fixtureId, e);
            return Optional.empty();
        }
    }

    public MatchStatResponseDto.TeamStatSummary getTeamStatSummary(Long fixtureId, Long teamId) {
        return getLiveSnapshot(fixtureId)
                .map(snapshot -> findTeamStat(snapshot, teamId))
                .orElseGet(() -> emptyTeamStat(teamId));
    }

    private MatchStatResponseDto.TeamStatSummary findTeamStat(LiveMatchSnapshotDto snapshot, Long teamId) {
        if (snapshot.getHomeTeamStat() != null && teamId.equals(snapshot.getHomeTeamStat().getTeamId())) {
            return snapshot.getHomeTeamStat();
        }
        if (snapshot.getAwayTeamStat() != null && teamId.equals(snapshot.getAwayTeamStat().getTeamId())) {
            return snapshot.getAwayTeamStat();
        }
        return emptyTeamStat(teamId);
    }

    private MatchStatResponseDto.TeamStatSummary emptyTeamStat(Long teamId) {
        return MatchStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .build();
    }

    private String latestEventKey(Long fixtureId) {
        return LATEST_EVENT_KEY.formatted(fixtureId);
    }

    private String liveSnapshotKey(Long fixtureId) {
        return LIVE_SNAPSHOT_KEY.formatted(fixtureId);
    }
}
