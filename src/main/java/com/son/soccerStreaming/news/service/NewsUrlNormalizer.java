package com.son.soccerStreaming.news.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class NewsUrlNormalizer {

    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "gclid", "fbclid", "mc_cid", "mc_eid"
    );

    private NewsUrlNormalizer() {
    }

    static String normalize(String value) {
        try {
            URI uri = URI.create(value.trim());
            String query = normalizeQuery(uri.getRawQuery());
            int port = uri.getPort();
            if (("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)) {
                port = -1;
            }
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    port,
                    uri.getPath(),
                    query,
                    null
            ).toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new IllegalArgumentException("Invalid news URL.", e);
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = Arrays.stream(query.split("&"))
                .filter(part -> {
                    String name = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !TRACKING_PARAMETERS.contains(name);
                })
                .sorted()
                .collect(Collectors.joining("&"));
        return normalized.isBlank() ? null : normalized;
    }
}
