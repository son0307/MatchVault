package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.dto.ApiFootballLeagueDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiFootballResponseValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsAValidEmptyResponse() throws Exception {
        ApiFootballLiveDto.ApiResponse<?> body = response("""
                {"errors": [], "results": 0, "response": []}
                """);

        assertThat(ApiFootballResponseValidator.validate("getEvents", HttpStatus.OK, body))
                .isSameAs(body);
    }

    @Test
    void deserializesTheLeagueEnvelopeWithTheProductionJacksonVersion() throws Exception {
        var responseType = new ParameterizedTypeReference<
                ApiFootballLeagueDto.ApiResponse<ApiFootballLeagueDto.LeagueResponse>>() { };
        var javaType = objectMapper.getTypeFactory().constructType(responseType.getType());
        ApiFootballLeagueDto.ApiResponse<ApiFootballLeagueDto.LeagueResponse> body = objectMapper.readValue("""
                {
                  "get": "leagues",
                  "parameters": {"id": "39"},
                  "errors": [],
                  "results": 1,
                  "paging": {"current": 1, "total": 1},
                  "response": [{
                    "league": {"id": 39, "name": "Premier League"},
                    "seasons": [{
                      "year": 2026,
                      "start": "2026-08-21",
                      "end": "2027-05-30",
                      "current": true,
                      "coverage": {
                        "fixtures": {
                          "events": false,
                          "lineups": false,
                          "statistics_fixtures": false,
                          "statistics_players": false
                        },
                        "standings": true,
                        "players": false,
                        "injuries": false
                      }
                    }]
                  }]
                }
                """, javaType);

        ApiFootballResponseValidator.validate("getLeagues", HttpStatus.OK, body);

        assertThat(body.getResponse()).hasSize(1);
        assertThat(body.getResponse().get(0).getLeague().getId()).isEqualTo(39);
        assertThat(body.getResponse().get(0).getSeasons().get(0).getYear()).isEqualTo(2026);
    }

    @Test
    void acceptsNoContentAsAValidEmptyResult() {
        ApiFootballLiveDto.ApiResponse<?> result = ApiFootballResponseValidator.validate(
                "getEvents", HttpStatus.NO_CONTENT, (ApiFootballLiveDto.ApiResponse<?>) null);

        assertThat(result).isNull();
    }

    @Test
    void rejectsAnEmptyBodyForARegularSuccessResponse() {
        assertThatThrownBy(() ->
                ApiFootballResponseValidator.validate("getEvents", HttpStatus.OK, null))
                .isInstanceOfSatisfying(ExternalApiException.class, exception -> {
                    assertThat(exception.getCategory()).isEqualTo(ExternalApiErrorCategory.INVALID_RESPONSE);
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void rejectsAResponseContainingProviderErrors() throws Exception {
        ApiFootballLiveDto.ApiResponse<?> body = response("""
                {
                  "errors": {"requests": "Invalid season"},
                  "results": 0,
                  "response": []
                }
                """);

        assertThatThrownBy(() ->
                ApiFootballResponseValidator.validate("getFixtures", HttpStatus.OK, body))
                .isInstanceOfSatisfying(ExternalApiException.class, exception -> {
                    assertThat(exception.getCategory()).isEqualTo(ExternalApiErrorCategory.INVALID_RESPONSE);
                    assertThat(exception.getHttpStatus()).isEqualTo(200);
                    assertThat(exception.getProviderErrorDetails())
                            .isEqualTo("{\"requests\":\"Invalid season\"}");
                });
    }

    @Test
    void rejectsAnInconsistentResultCount() throws Exception {
        ApiFootballLiveDto.ApiResponse<?> body = response("""
                {"errors": [], "results": 1, "response": []}
                """);

        assertThatThrownBy(() ->
                ApiFootballResponseValidator.validate("getFixtures", HttpStatus.OK, body))
                .isInstanceOf(ExternalApiException.class);
    }

    @SuppressWarnings("unchecked")
    private ApiFootballLiveDto.ApiResponse<?> response(String json) throws Exception {
        return objectMapper.readValue(json, ApiFootballLiveDto.ApiResponse.class);
    }
}
