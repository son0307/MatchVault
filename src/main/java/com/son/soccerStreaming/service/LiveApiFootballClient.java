package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.ApiFootballLiveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LiveApiFootballClient {

    private final RestClient liveApiRestClient;

    @Value("${live.api-football.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${live.api-football.api-key:}")
    private String apiKey;

    @Value("${live.api-football.api-host:}")
    private String apiHost;

    public List<ApiFootballLiveDto.FixtureResponse> getFixture(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = liveApiRestClient.get()
                .uri(baseUrl + "/fixtures?id={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.EventResponse> getEvents(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.EventResponse> body = liveApiRestClient.get()
                .uri(baseUrl + "/fixtures/events?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.FixturePlayersResponse> getPlayerStats(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixturePlayersResponse> body = liveApiRestClient.get()
                .uri(baseUrl + "/fixtures/players?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    private void setApiHeaders(HttpHeaders headers) {
        if (!apiKey.isBlank()) {
            headers.set("x-rapidapi-key", apiKey);
        }
        if (!apiHost.isBlank()) {
            headers.set("x-rapidapi-host", apiHost);
        }
    }

    private <T> List<T> responseOf(ApiFootballLiveDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }
}
