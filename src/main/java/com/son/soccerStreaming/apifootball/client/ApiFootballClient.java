package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLineupDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballPlayerDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballStandingDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballTeamDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiFootballClient {

    private final RestClient apiFootballRestClient;

    @Value("${live.api-football.base-url:https://v3.football.api-sports.io}")
    private String baseUrl;

    @Value("${live.api-football.api-key:}")
    private String apiKey;

    @Value("${live.api-football.api-host:}")
    private String apiHost;

    public List<ApiFootballLiveDto.FixtureResponse> getFixture(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures?id={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.FixtureResponse> getFixtures(Integer league, Integer season) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures?league={league}&season={season}", league, season)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.FixtureResponse> getLiveFixtures(Integer league) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures?league={league}&live=all", league)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.EventResponse> getEvents(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.EventResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures/events?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballLineupDto.LineupResponse> getLineups(Long fixtureId) {
        ApiFootballLineupDto.ApiResponse<ApiFootballLineupDto.LineupResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures/lineups?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return lineupResponseOf(body);
    }

    public List<ApiFootballPlayerDto.ProfileResponse> getPlayerProfiles(Long playerId) {
        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.ProfileResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/players/profiles?player={playerId}", playerId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return playerResponseOf(body);
    }

    public List<ApiFootballPlayerDto.SquadResponse> getPlayerSquad(Long teamId) {
        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.SquadResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/players/squads?team={teamId}", teamId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return playerResponseOf(body);
    }

    public List<ApiFootballInjuryDto.InjuryResponse> getInjuries(Integer league, Integer season) {
        ApiFootballInjuryDto.ApiResponse<ApiFootballInjuryDto.InjuryResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/injuries?league={league}&season={season}", league, season)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return injuryResponseOf(body);
    }

    public List<ApiFootballLiveDto.FixturePlayersResponse> getPlayerStats(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixturePlayersResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures/players?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return responseOf(body);
    }

    public List<ApiFootballTeamDto.TeamResponse> getTeams(Integer league, Integer season) {
        ApiFootballTeamDto.ApiResponse<ApiFootballTeamDto.TeamResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/teams?league={league}&season={season}", league, season)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return teamResponseOf(body);
    }

    public List<ApiFootballStandingDto.StandingResponse> getStandings(Integer league, Integer season) {
        ApiFootballStandingDto.ApiResponse<ApiFootballStandingDto.StandingResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/standings?league={league}&season={season}", league, season)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return standingResponseOf(body);
    }

    private void setApiHeaders(HttpHeaders headers) {
        if (!apiKey.isBlank()) {
            headers.set("x-apisports-key", apiKey);
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

    private <T> List<T> teamResponseOf(ApiFootballTeamDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }

    private <T> List<T> standingResponseOf(ApiFootballStandingDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }

    private <T> List<T> lineupResponseOf(ApiFootballLineupDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }

    private <T> List<T> playerResponseOf(ApiFootballPlayerDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }

    private <T> List<T> injuryResponseOf(ApiFootballInjuryDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }
}
