package com.son.ssegateway;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public record Snapshot(String event, byte[] bytes) {
    public static final Set<String> EVENTS = Set.of("LIVE_SNAPSHOT", "FIXTURE_EVENTS", "PLAYER_STATS");
    public static Snapshot data(String event, String json) {
        if (!EVENTS.contains(event) || json == null) throw new IllegalArgumentException("Unsupported snapshot");
        return frame(event, "event:" + event + "\ndata:" + json.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\ndata:") + "\n\n");
    }
    static Snapshot connect() { return frame("CONNECT", "event:CONNECT\ndata:Successfully connected!\n\n"); }
    static Snapshot heartbeat() { return frame("heartbeat", ":heartbeat\n\n"); }
    private static Snapshot frame(String event, String content) { return new Snapshot(event, content.getBytes(StandardCharsets.UTF_8)); }
}
