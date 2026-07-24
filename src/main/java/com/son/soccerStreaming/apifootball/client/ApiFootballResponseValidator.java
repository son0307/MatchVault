package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.dto.ApiFootballResponseEnvelope;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;

import java.util.List;

final class ApiFootballResponseValidator {

    private static final int MAX_PROVIDER_ERROR_LENGTH = 2_000;

    private ApiFootballResponseValidator() {
    }

    static <T extends ApiFootballResponseEnvelope<?>> T validate(
            String operation, HttpStatusCode status, T body) {
        if (status.value() == HttpStatus.NO_CONTENT.value()) {
            return body;
        }
        if (body == null) {
            throw invalidResponse(operation, status, "API-Football returned an empty response body");
        }

        JsonNode errors = body.getErrors();
        if (errors == null) {
            throw invalidResponse(operation, status, "API-Football response did not contain errors metadata");
        }
        if (!errors.isArray() && !errors.isObject()) {
            throw invalidResponse(operation, status, "API-Football returned an invalid errors field");
        }
        if (!errors.isEmpty()) {
            throw invalidResponse(operation, status, "API-Football returned an error response", safeErrors(errors));
        }

        Integer results = body.getResults();
        List<?> response = body.getResponse();
        if (results == null || results < 0 || response == null) {
            throw invalidResponse(operation, status, "API-Football response envelope was incomplete");
        }
        if (results != response.size()) {
            throw invalidResponse(operation, status, "API-Football response result count was inconsistent");
        }
        return body;
    }

    private static ExternalApiException invalidResponse(
            String operation, HttpStatusCode status, String message) {
        return invalidResponse(operation, status, message, null);
    }

    private static ExternalApiException invalidResponse(
            String operation, HttpStatusCode status, String message, String providerErrorDetails) {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                operation,
                ExternalApiErrorCategory.INVALID_RESPONSE,
                status.value(),
                false,
                null,
                message,
                null,
                providerErrorDetails
        );
    }

    private static String safeErrors(JsonNode errors) {
        String value = errors.toString().replaceAll("[\\r\\n\\t]", " ");
        return value.length() <= MAX_PROVIDER_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_PROVIDER_ERROR_LENGTH) + "...";
    }
}
