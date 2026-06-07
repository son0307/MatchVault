package com.son.soccerStreaming.media.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class MediaConfigurationValidator {

    private final MediaProperties properties;

    @PostConstruct
    public void validate() {
        if (!properties.getCache().isEnabled() && !properties.getR2().isEnabled()) {
            return;
        }

        requireHttpsUrl(properties.getPublicBaseUrl(), "media.public-base-url");

        if (properties.getR2().isEnabled()) {
            MediaProperties.R2 r2 = properties.getR2();
            requireHttpsUrl(r2.getEndpoint(), "media.r2.endpoint");
            requirePresent(r2.getBucket(), "media.r2.bucket");
            requirePresent(r2.getAccessKey(), "media.r2.access-key");
            requirePresent(r2.getSecretKey(), "media.r2.secret-key");
        }
    }

    private void requirePresent(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when media caching is enabled.");
        }
    }

    private void requireHttpsUrl(String value, String propertyName) {
        requirePresent(value, propertyName);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(propertyName + " must be a valid HTTPS URL.", e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException(propertyName + " must be a valid HTTPS URL.");
        }
    }
}
