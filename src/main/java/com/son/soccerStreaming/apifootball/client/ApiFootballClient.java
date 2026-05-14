package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballFixtureStatisticsDto;
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
import java.util.stream.Collectors;

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

    public List<ApiFootballLiveDto.FixtureResponse> getFixturesByIds(List<Long> fixtureIds) {
        String ids = fixtureIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));

        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures?ids={fixtureIds}", ids)
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

    public ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> getRegisteredPlayers(
            Integer league,
            Integer season,
            Integer page
    ) {
        if (page == null || page <= 1) {
            ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = apiFootballRestClient.get()
                    .uri(baseUrl + "/players?league={league}&season={season}", league, season)
                    .headers(this::setApiHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
        }

        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/players?league={league}&season={season}&page={page}", league, season, page)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
    }

    public ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> getRegisteredPlayersByTeam(
            Long teamId,
            Integer league,
            Integer season,
            Integer page
    ) {
        if (page == null || page <= 1) {
            ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = apiFootballRestClient.get()
                    .uri(baseUrl + "/players?league={league}&team={teamId}&season={season}", league, teamId, season)
                    .headers(this::setApiHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
        }

        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/players?league={league}&team={teamId}&season={season}&page={page}", league, teamId, season, page)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
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

    public List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> getFixtureStatistics(Long fixtureId) {
        ApiFootballFixtureStatisticsDto.ApiResponse<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> body = apiFootballRestClient.get()
                .uri(baseUrl + "/fixtures/statistics?fixture={fixtureId}", fixtureId)
                .headers(this::setApiHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return fixtureStatisticsResponseOf(body);
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

    private <T> List<T> fixtureStatisticsResponseOf(ApiFootballFixtureStatisticsDto.ApiResponse<T> body) {
        if (body == null || body.getResponse() == null) {
            return List.of();
        }
        return body.getResponse();
    }
}
