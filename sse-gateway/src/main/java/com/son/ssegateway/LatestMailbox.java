package com.son.ssegateway;

import java.util.LinkedHashMap;

/** One unsent complete snapshot per type, plus at most one in-flight frame. */
final class LatestMailbox {
    private final long maxBytes;
    private final LinkedHashMap<String, Snapshot> pending = new LinkedHashMap<>();
    private long pendingBytes;
    private Snapshot inFlight;
    private long progressAt;
    private boolean closed;
    LatestMailbox(long maxBytes) { this.maxBytes = maxBytes; }

    synchronized boolean offer(Snapshot frame, long now) {
        if (closed) return false;
        Snapshot previous = pending.get(frame.event());
        long bytes = pendingBytes - (previous == null ? 0 : previous.bytes().length) + frame.bytes().length;
        if (bytes + (inFlight == null ? 0 : inFlight.bytes().length) > maxBytes) return false;
        if (pending.isEmpty() && inFlight == null) progressAt = now;
        // Move a replacement to the tail so continuous updates cannot starve other kinds.
        pending.remove(frame.event()); pending.put(frame.event(), frame); pendingBytes = bytes;
        return true;
    }
    synchronized Snapshot take() {
        if (closed || inFlight != null || pending.isEmpty()) return null;
        var iterator = pending.values().iterator();
        inFlight = iterator.next(); iterator.remove(); pendingBytes -= inFlight.bytes().length;
        return inFlight;
    }
    synchronized void written(long now) { inFlight = null; progressAt = now; }
    synchronized void progressed(long now) { progressAt = now; }
    synchronized boolean stalled(long now, long limit) { return !closed && (inFlight != null || !pending.isEmpty()) && now - progressAt >= limit; }
    synchronized boolean idle() { return !closed && inFlight == null && pending.isEmpty(); }
    synchronized long bytes() { return pendingBytes + (inFlight == null ? 0 : inFlight.bytes().length); }
    synchronized void close() { closed = true; pending.clear(); pendingBytes = 0; inFlight = null; }
}
