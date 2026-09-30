package com.son.ssegateway;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Component
@ConditionalOnProperty(name = "sse.redis.enabled", havingValue = "true", matchIfMissing = true)
final class RedisRecovery {
    private final SubscriberHub hub;
    private final ObjectMapper mapper;
    private final WebClient client;
    private final int concurrency;
    private final AtomicLong epoch = new AtomicLong(), recovered = new AtomicLong(), failed = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "sse-state-recovery"); thread.setDaemon(true); return thread;
    });
    private volatile reactor.core.Disposable work;
    private volatile boolean stopped;

    RedisRecovery(SubscriberHub hub, ObjectMapper mapper,
                  @Value("${sse.recovery.mvc-base-url:http://127.0.0.1:8080}") String baseUrl,
                  @Value("${sse.recovery.concurrency:4}") int concurrency) {
        var uri = java.net.URI.create(baseUrl);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || concurrency < 1 || concurrency > 32)
            throw new IllegalArgumentException("Invalid SSE recovery configuration");
        this.hub = hub; this.mapper = mapper; this.concurrency = concurrency;
        client = WebClient.builder().baseUrl(baseUrl).codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)).build();
        worker.scheduleWithFixedDelay(this::poll, 0, 500, TimeUnit.MILLISECONDS);
    }
    void subscribed() { epoch.incrementAndGet(); worker.execute(this::poll); }
    private void poll() {
        if (stopped || epoch.get() == 0 || !running.compareAndSet(false, true)) return;
        long targetEpoch = epoch.get();
        work = Flux.fromIterable(hub.recoveryTickets(targetEpoch)).flatMap(ticket ->
                client.get().uri("/api/v1/live/recovery/fixtures/{id}", ticket.fixtureId())
                        .retrieve().bodyToMono(Bundle.class).timeout(Duration.ofSeconds(5))
                        .doOnNext(bundle -> {
                            if (epoch.get() != targetEpoch) return;
                            var frames = validate(ticket.fixtureId(), bundle);
                            if (hub.recover(ticket, frames)) recovered.incrementAndGet();
                        }).onErrorResume(error -> {
                            failed.incrementAndGet(); hub.recoveryFailed(ticket);
                            return Mono.empty();
                        }), concurrency)
                .doFinally(signal -> running.set(false)).subscribe();
    }
    List<Snapshot> validate(long fixtureId, Bundle bundle) {
        if (bundle.fixtureId() != fixtureId || bundle.events() == null || bundle.events().size() != 3)
            throw new IllegalArgumentException("Invalid recovery snapshot");
        var names = new HashSet<String>(); var frames = new ArrayList<Snapshot>();
        for (var event : bundle.events()) {
            var node = mapper.readTree(event.data());
            if (!names.add(event.event()) || node.path("fixtureId").asLong() != fixtureId)
                throw new IllegalArgumentException("Invalid recovery event");
            frames.add(Snapshot.data(event.event(), event.data()));
        }
        if (!names.equals(Snapshot.EVENTS)) throw new IllegalArgumentException("Missing recovery event");
        // Send final status last so the browser can apply the other two snapshots before closing SSE.
        frames.sort(Comparator.comparing(frame -> frame.event().equals("LIVE_SNAPSHOT")));
        return frames;
    }
    Map<String, Object> stats() { return Map.of("epoch", epoch.get(), "recoveredFixtures", recovered.get(), "failedReads", failed.get()); }
    @PreDestroy void close() { stopped = true; worker.shutdownNow(); if (work != null) work.dispose(); }
    record Event(String event, String data) {}
    record Bundle(long fixtureId, List<Event> events) {}
}
