package com.son.soccerStreaming.news.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "news")
public class NewsProperties {

    private SerpApi serpApi = new SerpApi();
    private Translation translation = new Translation();
    private Sync sync = new Sync();

    @Getter
    @Setter
    public static class SerpApi {
        private String baseUrl = "https://serpapi.com";
        private String apiKey = "";
        private int lookbackDays = 7;
        private int maxArticlesPerTeam = 20;
        private List<String> searchSites = new ArrayList<>(List.of(
                "bbc.com/sport/football",
                "skysports.com/football",
                "theguardian.com/football",
                "nytimes.com/athletic",
                "goal.com",
                "telegraph.co.uk/football",
                "football.london"
        ));
    }

    @Getter
    @Setter
    public static class Translation {
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "gpt-5.6-luna";
        private int batchSize = 50;
    }

    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = false;
        private int retentionDays = 90;
    }
}
