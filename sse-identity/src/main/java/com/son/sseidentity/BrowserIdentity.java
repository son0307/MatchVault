package com.son.sseidentity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

/** Shared wire format: MVC issues, Gateway verifies without a Redis dependency. */
public final class BrowserIdentity {
    public static final String COOKIE = "sse_browser";
    public static final String PATH = "/api/v1/live";
    public static final long MAX_AGE_SECONDS = 30L * 24 * 60 * 60;
    private final byte[] key;
    private final Clock clock;

    public BrowserIdentity(String secret, boolean local) { this(secret, local, Clock.systemUTC()); }

    public BrowserIdentity(String secret, boolean local, Clock clock) {
        if ((secret == null || secret.isBlank()) && local) secret = "local-only-sse-browser-identity-key";
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("Set SSE_IDENTITY_SECRET to the same random secret (at least 32 bytes) in MVC and Gateway");
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue() {
        String payload = UUID.randomUUID() + "." + (clock.instant().getEpochSecond() + MAX_AGE_SECONDS);
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    /** Returns a stable browser ID, or null. Never log the supplied cookie. */
    public String verify(String token) {
        if (token == null || token.length() > 160) return null;
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            String id = UUID.fromString(parts[0]).toString();
            long expiry = Long.parseLong(parts[1]);
            long now = clock.instant().getEpochSecond();
            if (!id.equals(parts[0]) || expiry <= now || expiry > now + MAX_AGE_SECONDS) return null;
            return MessageDigest.isEqual(sign(parts[0] + "." + parts[1]),
                    Base64.getUrlDecoder().decode(parts[2])) ? id : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
