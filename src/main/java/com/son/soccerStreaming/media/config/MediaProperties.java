package com.son.soccerStreaming.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    private String publicBaseUrl = "";
    private Cache cache = new Cache();
    private R2 r2 = new R2();
    private AdminMediaCleanup adminMediaCleanup = new AdminMediaCleanup();

    @Getter
    @Setter
    public static class Cache {
        private boolean enabled = false;
        private List<String> allowedHosts = new ArrayList<>(List.of("media.api-sports.io"));
        private long maxBytes = 2 * 1024 * 1024;
        private Duration requestTimeout = Duration.ofSeconds(5);
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration failureCooldown = Duration.ofHours(6);
        private Scheduler scheduler = new Scheduler();
    }

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = false;
        private int batchSize = 50;
    }

    @Getter
    @Setter
    public static class R2 {
        private boolean enabled = false;
        private String endpoint = "";
        private String bucket = "";
        private String region = "auto";
        private String accessKey = "";
        private String secretKey = "";
    }

    @Getter
    @Setter
    public static class AdminMediaCleanup {
        private boolean enabled = false;
        private Duration minimumAge = Duration.ofHours(1);
    }
}
