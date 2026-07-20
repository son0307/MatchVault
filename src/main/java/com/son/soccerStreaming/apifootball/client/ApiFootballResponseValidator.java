package com.son.soccerStreaming.apifootball.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.son.soccerStreaming.apifootball.dto.ApiFootballResponseEnvelope;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;

final class ApiFootballResponseValidator {

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
        if (!errors.isContainerNode() || !errors.isEmpty()) {
            throw invalidResponse(operation, status, "API-Football returned an error response");
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
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                operation,
                ExternalApiErrorCategory.INVALID_RESPONSE,
                status.value(),
                false,
                null,
                message,
                null
        );
    }
}
