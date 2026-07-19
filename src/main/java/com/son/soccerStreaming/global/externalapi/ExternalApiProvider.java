package com.son.soccerStreaming.global.externalapi;

public enum ExternalApiProvider {
    API_FOOTBALL("api-football", "API-Football"),
    SERP_API("serp-api", "SerpAPI"),
    OPENAI("openai", "OpenAI");

    private final String statusKey;
    private final String displayName;

    ExternalApiProvider(String statusKey, String displayName) {
        this.statusKey = statusKey;
        this.displayName = displayName;
    }

    public String statusKey() { return statusKey; }
    public String displayName() { return displayName; }
}
