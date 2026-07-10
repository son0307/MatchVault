package com.son.soccerStreaming.apifootball.client;

import org.springframework.web.client.RestClientException;

public class ApiFootballCircuitOpenException extends RestClientException {

    public ApiFootballCircuitOpenException(String message) {
        super(message);
    }
}
