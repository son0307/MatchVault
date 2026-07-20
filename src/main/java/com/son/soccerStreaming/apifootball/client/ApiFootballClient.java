package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballFixtureStatisticsDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLeagueDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLineupDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballPlayerDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballStandingDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballTeamDto;
import lombok.RequiredArgsConstructor;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiExecutor;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
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
    private final ApiFootballCircuitBreaker apiFootballCircuitBreaker;
    private final ExternalApiExecutor externalApiExecutor;

    @Value("${live.api-football.base-url:https://v3.football.api-sports.io}")
    private String baseUrl;

    @Value("${live.api-football.api-key:}")
    private String apiKey;

    @Value("${live.api-football.api-host:}")
    private String apiHost;

    public List<ApiFootballLiveDto.FixtureResponse> getFixture(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = get(
                "getFixture",
                "/fixtures?id={fixtureId}",
                new ParameterizedTypeReference<>() {
                },
                fixtureId
        );

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.FixtureResponse> getFixturesByIds(List<Long> fixtureIds) {
        String ids = fixtureIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));

        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = get(
                "getFixturesByIds",
                "/fixtures?ids={fixtureIds}",
                new ParameterizedTypeReference<>() {
                },
                ids
        );

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.FixtureResponse> getFixtures(Integer league, Integer season) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = get(
                "getFixtures",
                "/fixtures?league={league}&season={season}",
                new ParameterizedTypeReference<>() {
                },
                league,
                season
        );

        return responseOf(body);
    }

    public List<ApiFootballLeagueDto.LeagueResponse> getLeagueSeasons(Integer league) {
        ApiFootballLeagueDto.ApiResponse<ApiFootballLeagueDto.LeagueResponse> body = get(
                "getLeagueSeasons",
                "/leagues?id={league}",
                new ParameterizedTypeReference<>() {
                },
                league
        );

        return leagueResponseOf(body);
    }

    public List<ApiFootballLiveDto.FixtureResponse> getLiveFixtures(Integer league) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixtureResponse> body = get(
                "getLiveFixtures",
                "/fixtures?league={league}&live=all",
                new ParameterizedTypeReference<>() {
                },
                league
        );

        return responseOf(body);
    }

    public List<ApiFootballLiveDto.EventResponse> getEvents(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.EventResponse> body = get(
                "getEvents",
                "/fixtures/events?fixture={fixtureId}",
                new ParameterizedTypeReference<>() {
                },
                fixtureId
        );

        return responseOf(body);
    }

    public List<ApiFootballLineupDto.LineupResponse> getLineups(Long fixtureId) {
        ApiFootballLineupDto.ApiResponse<ApiFootballLineupDto.LineupResponse> body = get(
                "getLineups",
                "/fixtures/lineups?fixture={fixtureId}",
                new ParameterizedTypeReference<>() {
                },
                fixtureId
        );

        return lineupResponseOf(body);
    }

    public List<ApiFootballPlayerDto.ProfileResponse> getPlayerProfiles(Long playerId) {
        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.ProfileResponse> body = get(
                "getPlayerProfiles",
                "/players/profiles?player={playerId}",
                new ParameterizedTypeReference<>() {
                },
                playerId
        );

        return playerResponseOf(body);
    }

    public ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> getRegisteredPlayers(
            Integer league,
            Integer season,
            Integer page
    ) {
        if (page == null || page <= 1) {
            ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = get(
                    "getRegisteredPlayers",
                    "/players?league={league}&season={season}",
                    new ParameterizedTypeReference<>() {
                    },
                    league,
                    season
            );

            return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
        }

        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = get(
                "getRegisteredPlayers",
                "/players?league={league}&season={season}&page={page}",
                new ParameterizedTypeReference<>() {
                },
                league,
                season,
                page
        );

        return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
    }

    public ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> getRegisteredPlayersByTeam(
            Long teamId,
            Integer league,
            Integer season,
            Integer page
    ) {
        if (page == null || page <= 1) {
            ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = get(
                    "getRegisteredPlayersByTeam",
                    "/players?league={league}&team={teamId}&season={season}",
                    new ParameterizedTypeReference<>() {
                    },
                    league,
                    teamId,
                    season
            );

            return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
        }

        ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> body = get(
                "getRegisteredPlayersByTeam",
                "/players?league={league}&team={teamId}&season={season}&page={page}",
                new ParameterizedTypeReference<>() {
                },
                league,
                teamId,
                season,
                page
        );

        return body != null ? body : new ApiFootballPlayerDto.ApiResponse<>();
    }

    public List<ApiFootballInjuryDto.InjuryResponse> getInjuries(Integer league, Integer season) {
        ApiFootballInjuryDto.ApiResponse<ApiFootballInjuryDto.InjuryResponse> body = get(
                "getInjuries",
                "/injuries?league={league}&season={season}",
                new ParameterizedTypeReference<>() {
                },
                league,
                season
        );

        return injuryResponseOf(body);
    }

    public List<ApiFootballLiveDto.FixturePlayersResponse> getPlayerStats(Long fixtureId) {
        ApiFootballLiveDto.ApiResponse<ApiFootballLiveDto.FixturePlayersResponse> body = get(
                "getPlayerStats",
                "/fixtures/players?fixture={fixtureId}",
                new ParameterizedTypeReference<>() {
                },
                fixtureId
        );

        return responseOf(body);
    }

    public List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> getFixtureStatistics(Long fixtureId) {
        ApiFootballFixtureStatisticsDto.ApiResponse<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> body = get(
                "getFixtureStatistics",
                "/fixtures/statistics?fixture={fixtureId}",
                new ParameterizedTypeReference<>() {
                },
                fixtureId
        );

        return fixtureStatisticsResponseOf(body);
    }

    public List<ApiFootballTeamDto.TeamResponse> getTeams(Integer league, Integer season) {
        ApiFootballTeamDto.ApiResponse<ApiFootballTeamDto.TeamResponse> body = get(
                "getTeams",
                "/teams?league={league}&season={season}",
                new ParameterizedTypeReference<>() {
                },
                league,
                season
        );

        return teamResponseOf(body);
    }

    public List<ApiFootballStandingDto.StandingResponse> getStandings(Integer league, Integer season) {
        ApiFootballStandingDto.ApiResponse<ApiFootballStandingDto.StandingResponse> body = get(
                "getStandings",
                "/standings?league={league}&season={season}",
                new ParameterizedTypeReference<>() {
                },
                league,
                season
        );

        return standingResponseOf(body);
    }

    private <T> T get(String operation, String path, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        try {
            T response = externalApiExecutor.execute(ExternalApiProvider.API_FOOTBALL, operation, () -> {
                apiFootballCircuitBreaker.beforeRequest(operation);
                return apiFootballRestClient.get()
                        .uri(baseUrl + path, uriVariables)
                        .headers(this::setApiHeaders)
                        .retrieve()
                        .body(responseType);
            });
            apiFootballCircuitBreaker.recordSuccess();
            return response;
        } catch (ExternalApiException exception) {
            apiFootballCircuitBreaker.recordFailure(operation, exception);
            throw exception;
        }
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

    private <T> List<T> leagueResponseOf(ApiFootballLeagueDto.ApiResponse<T> body) {
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
