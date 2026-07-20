package com.son.soccerStreaming.apifootball.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
                .isInstanceOfSatisfying(ExternalApiException.class, exception ->
                        assertThat(exception.getCategory()).isEqualTo(ExternalApiErrorCategory.INVALID_RESPONSE));
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
