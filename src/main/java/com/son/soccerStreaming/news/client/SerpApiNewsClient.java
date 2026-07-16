package com.son.soccerStreaming.news.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.son.soccerStreaming.news.config.NewsProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SerpApiNewsClient {

    private final RestClient restClient;
    private final NewsProperties properties;

    public SerpApiNewsClient(
            @Qualifier("serpApiRestClient") RestClient restClient,
            NewsProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public List<SearchArticle> searchTeamNews(String teamName) {
        String apiKey = properties.getSerpApi().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("SerpApi API key is not configured.");
        }

        SerpApiResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search.json")
                        .queryParam("engine", "google_news")
                        .queryParam("q", buildQuery(teamName))
                        .queryParam("gl", "uk")
                        .queryParam("hl", "en")
                        .queryParam("api_key", apiKey)
                        .build())
                .retrieve()
                .body(SerpApiResponse.class);

        if (response == null || response.newsResults() == null) {
            return List.of();
        }

        List<SearchArticle> flattened = new ArrayList<>();
        response.newsResults().forEach(result -> flatten(result, flattened));

        Map<String, SearchArticle> uniqueByUrl = new LinkedHashMap<>();
        flattened.stream()
                .filter(article -> isAllowedUrl(article.url()))
                .forEach(article -> uniqueByUrl.putIfAbsent(article.url(), article));

        return uniqueByUrl.values().stream()
                .limit(properties.getSerpApi().getMaxArticlesPerTeam())
                .toList();
    }

    String buildQuery(String teamName) {
        String sites = properties.getSerpApi().getSearchSites().stream()
                .filter(StringUtils::hasText)
                .map(site -> "site:" + site.trim())
                .reduce((left, right) -> left + " OR " + right)
                .map(value -> "(" + value + ")")
                .orElseThrow(() -> new IllegalStateException("News search sites are not configured."));
        return "\"%s\" football when:%dd %s".formatted(
                teamName,
                properties.getSerpApi().getLookbackDays(),
                sites
        );
    }

    boolean isAllowedUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return false;
            }
            String host = normalizeHost(uri.getHost());
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return switch (host) {
                case "bbc.com" -> path.startsWith("/sport/football");
                case "skysports.com", "telegraph.co.uk", "theguardian.com" -> path.startsWith("/football");
                case "nytimes.com" -> path.startsWith("/athletic");
                case "goal.com", "football.london" -> true;
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String publisherDomain(String value) {
        String host = normalizeHost(URI.create(value).getHost());
        return host;
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private void flatten(SerpNewsResult result, List<SearchArticle> target) {
        if (result == null) {
            return;
        }
        if (result.highlight() != null) {
            flatten(result.highlight(), target);
        }
        if (result.stories() != null) {
            result.stories().forEach(story -> flatten(story, target));
        }
        if (StringUtils.hasText(result.title()) && StringUtils.hasText(result.link())) {
            String title = result.title().trim();
            String link = result.link().trim();
            if (title.length() > 1000 || link.length() > 2048 || !isAllowedUrl(link)) {
                return;
            }
            String publisher = result.source() != null && StringUtils.hasText(result.source().name())
                    ? result.source().name().trim()
                    : publisherDomain(link);
            if (publisher.length() > 255) {
                publisher = publisher.substring(0, 255);
            }
            target.add(new SearchArticle(
                    title,
                    link,
                    publisher,
                    parseInstant(result.isoDate())
            ));
        }
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record SearchArticle(String title, String url, String publisherName, Instant publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SerpApiResponse(@JsonProperty("news_results") List<SerpNewsResult> newsResults) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SerpNewsResult(
            String title,
            String link,
            SerpSource source,
            @JsonProperty("iso_date") String isoDate,
            List<SerpNewsResult> stories,
            SerpNewsResult highlight
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SerpSource(String name) {
    }
}
