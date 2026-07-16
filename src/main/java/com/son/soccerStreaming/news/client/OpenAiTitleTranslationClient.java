package com.son.soccerStreaming.news.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.news.config.NewsProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiTitleTranslationClient {

    private static final String SYSTEM_PROMPT = """
            You translate English football news headlines into natural Korean headlines.
            Translate only the supplied title. Do not summarize, add facts, remove claims, or editorialize.
            Preserve club, player, competition, and publication proper nouns accurately.
            Return exactly one result for every supplied articleId.
            """;

    private final RestClient restClient;
    private final NewsProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiTitleTranslationClient(
            @Qualifier("openAiNewsRestClient") RestClient restClient,
            NewsProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<Long, String> translate(List<TranslationInput> inputs) {
        if (inputs.isEmpty()) {
            return Map.of();
        }
        String apiKey = properties.getTranslation().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getTranslation().getModel());
        request.put("store", false);
        request.put("input", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", writeJson(inputs))
        ));
        request.put("text", Map.of("format", translationFormat()));
        request.put("max_output_tokens", Math.max(1000, inputs.size() * 80));

        JsonNode response = restClient.post()
                .uri("/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        String outputText = extractOutputText(response);
        try {
            TranslationEnvelope envelope = objectMapper.readValue(outputText, TranslationEnvelope.class);
            Map<Long, String> translated = new LinkedHashMap<>();
            if (envelope.translations() != null) {
                envelope.translations().forEach(item -> {
                    if (item.articleId() != null && StringUtils.hasText(item.translatedTitle())) {
                        translated.put(item.articleId(), item.translatedTitle().trim());
                    }
                });
            }
            return translated;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI returned an invalid title translation response.", e);
        }
    }

    private Map<String, Object> translationFormat() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "properties", Map.of(
                        "articleId", Map.of("type", "integer"),
                        "translatedTitle", Map.of("type", "string")
                ),
                "required", List.of("articleId", "translatedTitle"),
                "additionalProperties", false
        );
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "translations", Map.of("type", "array", "items", item)
                ),
                "required", List.of("translations"),
                "additionalProperties", false
        );
        return Map.of(
                "type", "json_schema",
                "name", "news_title_translations",
                "strict", true,
                "schema", schema
        );
    }

    private String extractOutputText(JsonNode response) {
        if (response != null) {
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    String text = content.path("text").asText("");
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output text.");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize title translation input.", e);
        }
    }

    public record TranslationInput(Long articleId, String originalTitle) {
    }

    record TranslationEnvelope(List<TranslationResult> translations) {
    }

    record TranslationResult(Long articleId, String translatedTitle) {
    }
}
