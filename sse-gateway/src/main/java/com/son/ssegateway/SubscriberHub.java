package com.son.ssegateway;

import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class SubscriberHub {
    static final class Room extends ConcurrentHashMap<String, Subscriber> {
        long generation, recoveredEpoch, retryAt;
    }
    record RecoveryTicket(long fixtureId, Room room, long generation, long epoch) {}
    private final ConcurrentHashMap<Long, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> closed = new ConcurrentHashMap<>();
    private final long maxBytes, stallMs;
    public SubscriberHub(@Value("${sse.mailbox-bytes:1048576}") long maxBytes,
                         @Value("${sse.stall-ms:15000}") long stallMs) {
        if (maxBytes < 1024 || maxBytes > 16 * 1024 * 1024 || stallMs < 100 || stallMs > 300000)
            throw new IllegalArgumentException("Invalid SSE limits");
        this.maxBytes = maxBytes; this.stallMs = stallMs;
    }
    public Flux<byte[]> subscribe(long fixtureId, Channel channel, String marker) {
        String diagnostic = marker != null && marker.matches("slow-[1-9][0-9]{0,3}") ? marker : "normal";
        return Flux.create(sink -> {
            String id = UUID.randomUUID().toString();
            Subscriber subscriber = new Subscriber(channel, sink, maxBytes, stallMs, diagnostic, reason -> {
                closed.computeIfAbsent(reason, key -> new LongAdder()).increment();
                rooms.computeIfPresent(fixtureId, (key, room) -> { room.remove(id); return room.isEmpty() ? null : room; });
            });
            // start() executes on Netty's event loop, prior to publishing into the room.
            channel.eventLoop().execute(() -> {
                if (!channel.isActive() || sink.isCancelled()) { subscriber.close("client_closed"); return; }
                subscriber.start();
                rooms.compute(fixtureId, (key, room) -> {
                    if (room == null) room = new Room(); room.put(id, subscriber); return room;
                });
            });
        }, FluxSink.OverflowStrategy.ERROR);
    }
    public void publish(long fixtureId, Snapshot snapshot) {
        rooms.computeIfPresent(fixtureId, (id, room) -> {
            room.generation++;
            room.values().forEach(client -> client.offer(snapshot));
            return room.isEmpty() ? null : room;
        });
    }
    java.util.List<RecoveryTicket> recoveryTickets(long epoch) {
        var result = new java.util.ArrayList<RecoveryTicket>();
        rooms.forEach((id, ignored) -> rooms.computeIfPresent(id, (key, room) -> {
            if (room.recoveredEpoch < epoch && System.nanoTime() >= room.retryAt)
                result.add(new RecoveryTicket(id, room, room.generation, epoch));
            return room;
        }));
        return result;
    }
    boolean recover(RecoveryTicket ticket, java.util.List<Snapshot> snapshots) {
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        rooms.computeIfPresent(ticket.fixtureId(), (id, room) -> {
            // Room replacement or newer Redis data during the HTTP request invalidates this read.
            if (room != ticket.room() || room.generation != ticket.generation()) return room;
            snapshots.forEach(snapshot -> room.values().forEach(client -> client.offer(snapshot)));
            room.recoveredEpoch = ticket.epoch();
            accepted.set(true);
            return room.isEmpty() ? null : room;
        });
        return accepted.get();
    }
    void recoveryFailed(RecoveryTicket ticket) {
        rooms.computeIfPresent(ticket.fixtureId(), (id, room) -> {
            if (room == ticket.room()) room.retryAt = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            return room;
        });
    }
    public Map<String, Object> stats() {
        long count = rooms.values().stream().mapToLong(Map::size).sum();
        long bytes = rooms.values().stream().flatMap(room -> room.values().stream()).mapToLong(Subscriber::bytes).sum();
        Map<String, Long> reasons = new java.util.TreeMap<>(); closed.forEach((key, value) -> reasons.put(key, value.sum()));
        return Map.of("subscribers", count, "retainedBytes", bytes, "closed", reasons);
    }
    @PreDestroy public void shutdown() { rooms.values().forEach(room -> room.values().forEach(client -> client.close("shutdown"))); }
}
