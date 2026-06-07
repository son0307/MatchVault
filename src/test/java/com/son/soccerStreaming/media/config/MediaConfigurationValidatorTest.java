package com.son.soccerStreaming.media.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaConfigurationValidatorTest {

    @Test
    void skipsValidationWhenMediaFeaturesAreDisabled() {
        MediaProperties properties = new MediaProperties();
        MediaConfigurationValidator validator = new MediaConfigurationValidator(properties);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void requiresPublicBaseUrlWhenCacheIsEnabled() {
        MediaProperties properties = new MediaProperties();
        properties.getCache().setEnabled(true);
        MediaConfigurationValidator validator = new MediaConfigurationValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media.public-base-url");
    }

    @Test
    void requiresHttpsR2EndpointWhenR2IsEnabled() {
        MediaProperties properties = new MediaProperties();
        properties.setPublicBaseUrl("https://media.example.com");
        properties.getR2().setEnabled(true);
        properties.getR2().setEndpoint("http://example.com");
        properties.getR2().setBucket("bucket");
        properties.getR2().setAccessKey("access-key");
        properties.getR2().setSecretKey("secret-key");
        MediaConfigurationValidator validator = new MediaConfigurationValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media.r2.endpoint");
    }
}
