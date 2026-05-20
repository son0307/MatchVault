package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.dto.FixtureEventDto;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureDetailSyncService {

    private static final int MAX_IDS_PER_REQUEST = 20;

    private final ApiFootballClient apiFootballClient;
    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;
    private final ApiFootballFixtureLineupSyncService apiFootballFixtureLineupSyncService;
    private final ApiFootballFixtureStatSyncService apiFootballFixtureStatSyncService;
    private final ApiFootballFixturePlayerStatSyncService apiFootballFixturePlayerStatSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Transactional
    public FixtureDetailSyncResult syncFixtureDetail(Long fixtureId, boolean applyLiveStandingImpact) {
        return apiFootballClient.getFixture(fixtureId).stream()
                .findFirst()
                .map(response -> syncFixtureDetail(response, applyLiveStandingImpact))
                .orElseGet(() -> FixtureDetailSyncResult.empty(fixtureId));
    }

    @Transactional
    public FixtureDetailSyncResult syncFixtureDetail(ApiFootballLiveDto.FixtureResponse response,
                                                     boolean applyLiveStandingImpact) {
        StopWatch stopWatch = new StopWatch("fixture-detail-" + fixtureIdOf(response));
        stopWatch.start("fixture");
        Optional<Fixture> fixture = apiFootballFixtureSyncService.syncFixtureResponse(response, applyLiveStandingImpact);
        stopWatch.stop();
        if (fixture.isEmpty()) {
            Long fixtureId = response.getFixture() != null ? response.getFixture().getId() : null;
            return FixtureDetailSyncResult.empty(fixtureId);
        }

        stopWatch.start("events");
        FixtureEventDto latestEvent = apiFootballFixtureEventSyncService.syncEvents(fixture.get(), response.getEvents());
        stopWatch.stop();

        stopWatch.start("lineups");
        int lineups = apiFootballFixtureLineupSyncService.syncLineups(fixture.get(), response.getLineups());
        stopWatch.stop();

        stopWatch.start("teamStats");
        int teamStats = apiFootballFixtureStatSyncService.syncFixtureStats(fixture.get(), response.getStatistics());
        stopWatch.stop();

        stopWatch.start("playerStats");
        int playerStats = apiFootballFixturePlayerStatSyncService.syncPlayerStats(fixture.get(), response.getPlayers());
        stopWatch.stop();

        log.info("API-Football fixture detail processed. fixtureId={}, totalMs={}, {}",
                fixture.get().getFixtureId(),
                stopWatch.getTotalTimeMillis(),
                shortSummary(stopWatch));

        return FixtureDetailSyncResult.builder()
                .fixtureId(fixture.get().getFixtureId())
                .latestEvent(latestEvent)
                .lineupCount(lineups)
                .teamStatCount(teamStats)
                .playerStatCount(playerStats)
                .build();
    }

    public int syncFixtureDetails(List<Fixture> fixtures, boolean applyLiveStandingImpact) {
        return syncFixtureDetailsWithResults(fixtures, applyLiveStandingImpact).size();
    }

    public List<FixtureDetailSyncResult> syncFixtureDetailsWithResults(List<Fixture> fixtures, boolean applyLiveStandingImpact) {
        List<Long> fixtureIds = fixtures.stream()
                .map(Fixture::getFixtureId)
                .toList();
        return syncFixtureDetailsByIdsWithResults(fixtureIds, applyLiveStandingImpact);
    }

    public int syncFixtureDetailsByIds(List<Long> fixtureIds, boolean applyLiveStandingImpact) {
        return syncFixtureDetailsByIdsWithResults(fixtureIds, applyLiveStandingImpact).size();
    }

    public List<FixtureDetailSyncResult> syncFixtureDetailsByIdsWithResults(List<Long> fixtureIds, boolean applyLiveStandingImpact) {
        List<FixtureDetailSyncResult> results = new ArrayList<>();
        List<List<Long>> chunks = chunks(fixtureIds);
        for (int i = 0; i < chunks.size(); i++) {
            List<Long> chunk = chunks.get(i);
            StopWatch chunkWatch = new StopWatch("fixture-detail-chunk-" + (i + 1));
            try {
                log.info("API-Football fixture detail chunk started. chunk={}/{}, size={}, fixtureIds={}",
                        i + 1, chunks.size(), chunk.size(), chunk);

                chunkWatch.start("api");
                List<ApiFootballLiveDto.FixtureResponse> responses = apiFootballClient.getFixturesByIds(chunk);
                chunkWatch.stop();

                chunkWatch.start("upsert");
                List<FixtureDetailSyncResult> chunkResults = transactionTemplate.execute(status -> {
                    List<FixtureDetailSyncResult> processed = new ArrayList<>();
                    for (ApiFootballLiveDto.FixtureResponse response : responses) {
                        FixtureDetailSyncResult result = syncFixtureDetail(response, applyLiveStandingImpact);
                        if (result.fixtureId() != null) {
                            processed.add(result);
                        }
                    }
                    // Admin-triggered bulk sync runs in a web request, so clear managed entities per chunk.
                    entityManager.flush();
                    entityManager.clear();
                    return processed;
                });
                results.addAll(chunkResults);
                chunkWatch.stop();

                log.info("API-Football fixture detail chunk completed. chunk={}/{}, responseCount={}, totalMs={}, {}",
                        i + 1, chunks.size(), responses.size(), chunkWatch.getTotalTimeMillis(), shortSummary(chunkWatch));
            } catch (Exception e) {
                if (chunkWatch.isRunning()) {
                    chunkWatch.stop();
                }
                log.error("API-Football fixture detail chunk sync failed. fixtureIds={}", chunk, e);
            }
        }
        return results;
    }

    public int syncAllStoredFixtureDetails(boolean applyLiveStandingImpact) {
        return syncFixtureDetails(fixtureRecordRepository.findAllByOrderByFixtureDateAsc(), applyLiveStandingImpact);
    }

    public int syncSeasonFixtureDetails(Integer season, boolean applyLiveStandingImpact) {
        return syncFixtureDetails(fixtureRecordRepository.findAllBySeasonOrderByFixtureDateAsc(season), applyLiveStandingImpact);
    }

    private List<List<Long>> chunks(List<Long> fixtureIds) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < fixtureIds.size(); i += MAX_IDS_PER_REQUEST) {
            chunks.add(fixtureIds.subList(i, Math.min(i + MAX_IDS_PER_REQUEST, fixtureIds.size())));
        }
        return chunks;
    }

    private Long fixtureIdOf(ApiFootballLiveDto.FixtureResponse response) {
        return response != null && response.getFixture() != null ? response.getFixture().getId() : null;
    }

    private String shortSummary(StopWatch stopWatch) {
        StringBuilder builder = new StringBuilder("tasks=[");
        StopWatch.TaskInfo[] taskInfo = stopWatch.getTaskInfo();
        for (int i = 0; i < taskInfo.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(taskInfo[i].getTaskName())
                    .append("=")
                    .append(taskInfo[i].getTimeMillis())
                    .append("ms");
        }
        return builder.append("]").toString();
    }

    public record FixtureDetailSyncResult(
            Long fixtureId,
            FixtureEventDto latestEvent,
            int lineupCount,
            int teamStatCount,
            int playerStatCount
    ) {
        static FixtureDetailSyncResult empty(Long fixtureId) {
            return new FixtureDetailSyncResult(fixtureId, null, 0, 0, 0);
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private Long fixtureId;
            private FixtureEventDto latestEvent;
            private int lineupCount;
            private int teamStatCount;
            private int playerStatCount;

            Builder fixtureId(Long fixtureId) {
                this.fixtureId = fixtureId;
                return this;
            }

            Builder latestEvent(FixtureEventDto latestEvent) {
                this.latestEvent = latestEvent;
                return this;
            }

            Builder lineupCount(int lineupCount) {
                this.lineupCount = lineupCount;
                return this;
            }

            Builder teamStatCount(int teamStatCount) {
                this.teamStatCount = teamStatCount;
                return this;
            }

            Builder playerStatCount(int playerStatCount) {
                this.playerStatCount = playerStatCount;
                return this;
            }

            FixtureDetailSyncResult build() {
                return new FixtureDetailSyncResult(fixtureId, latestEvent, lineupCount, teamStatCount, playerStatCount);
            }
        }
    }
}
