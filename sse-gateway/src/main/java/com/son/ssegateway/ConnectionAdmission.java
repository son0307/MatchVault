package com.son.ssegateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

@Component
final class ConnectionAdmission {
    private final int maxConnections, rate, burst;
    private final LongSupplier clock;
    private static final long TOKEN = 1_000_000_000L;
    private long tokenUnits;
    private long updatedAt, rateRejected, capacityRejected;
    private int active;
    private final int maxPerIp, maxPerBrowser;
    private final Map<String, Integer> ips = new HashMap<>(), browsers = new HashMap<>();
    private long ipRejected, browserRejected;

    @Autowired
    ConnectionAdmission(@Value("${sse.max-connections:1000}") int maxConnections,
                        @Value("${sse.connect-rate-per-second:100}") int rate,
                        @Value("${sse.connect-burst:100}") int burst,
                        @Value("${sse.max-connections-per-ip:100}") int maxPerIp,
                        @Value("${sse.max-connections-per-browser:3}") int maxPerBrowser) {
        this(maxConnections, rate, burst, maxPerIp, maxPerBrowser, System::nanoTime);
    }

    ConnectionAdmission(int maxConnections, int rate, int burst, LongSupplier clock) {
        this(maxConnections, rate, burst, 100, 3, clock);
    }

    ConnectionAdmission(int maxConnections, int rate, int burst, int maxPerIp, int maxPerBrowser, LongSupplier clock) {
        if (maxConnections < 1 || rate < 1 || burst < 1) throw new IllegalArgumentException("Invalid admission limits");
        if (maxPerIp < 1 || maxPerBrowser < 1) throw new IllegalArgumentException("Invalid subscriber limits");
        this.maxPerIp = maxPerIp; this.maxPerBrowser = maxPerBrowser;
        this.maxConnections = maxConnections; this.rate = rate; this.burst = burst; this.clock = clock;
        tokenUnits = burst * TOKEN; updatedAt = clock.getAsLong();
    }

    synchronized Permit acquire(SubscriberIdentity.Key key) {
        long now = clock.getAsLong();
        long elapsed = Math.max(0, now - updatedAt);
        long missing = burst * TOKEN - tokenUnits;
        // Integer units avoid rejecting an exact refill boundary due to floating-point rounding.
        tokenUnits = elapsed >= (missing + rate - 1) / rate ? burst * TOKEN : tokenUnits + elapsed * rate;
        updatedAt = now;
        if (tokenUnits < TOKEN) { rateRejected++; throw new Rejected("rate_limit"); }
        // Capacity rejections consume a token; close returns a slot, never a rate token.
        tokenUnits -= TOKEN;
        if (active >= maxConnections) { capacityRejected++; throw new Rejected("connection_limit"); }
        if (ips.getOrDefault(key.ip(), 0) >= maxPerIp) { ipRejected++; throw new Rejected("ip_connection_limit"); }
        if (browsers.getOrDefault(key.browser(), 0) >= maxPerBrowser) { browserRejected++; throw new Rejected("browser_connection_limit"); }
        active++;
        ips.merge(key.ip(), 1, Integer::sum);
        browsers.merge(key.browser(), 1, Integer::sum);
        return new Permit(key);
    }

    synchronized Map<String, Object> stats() {
        var result = new HashMap<String, Object>();
        result.putAll(Map.of("active", active, "maxConnections", maxConnections, "ratePerSecond", rate,
                "burst", burst, "rateRejected", rateRejected, "capacityRejected", capacityRejected));
        result.putAll(Map.of("maxPerIp", maxPerIp, "maxPerBrowser", maxPerBrowser,
                "ipRejected", ipRejected, "browserRejected", browserRejected,
                "activeIps", ips.size(), "activeBrowsers", browsers.size()));
        return result;
    }

    final class Permit implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        private final SubscriberIdentity.Key key;
        Permit(SubscriberIdentity.Key key) { this.key = key; }
        @Override public void close() {
            if (released.compareAndSet(false, true)) synchronized (ConnectionAdmission.this) {
                active--;
                ips.computeIfPresent(key.ip(), (ignored, count) -> count == 1 ? null : count - 1);
                browsers.computeIfPresent(key.browser(), (ignored, count) -> count == 1 ? null : count - 1);
            }
        }
    }

    static final class Rejected extends RuntimeException {
        final String reason;
        Rejected(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
