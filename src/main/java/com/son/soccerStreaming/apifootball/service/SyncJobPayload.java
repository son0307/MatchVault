package com.son.soccerStreaming.apifootball.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Primitive arguments are persisted so another application instance can execute the job. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncJobPayload(Integer league, Integer season) {
}
