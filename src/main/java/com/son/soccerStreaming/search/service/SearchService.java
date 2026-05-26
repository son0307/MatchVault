package com.son.soccerStreaming.search.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.search.dto.SearchResponseDto;
import com.son.soccerStreaming.search.dto.SearchType;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int RESULT_LIMIT = 10;

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;

    @Transactional(readOnly = true)
    public SearchResponseDto search(String keyword) {
        return search(keyword, SearchType.ALL);
    }

    @Transactional(readOnly = true)
    public SearchResponseDto search(String keyword, SearchType type) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return SearchResponseDto.empty();
        }

        List<String> tokens = tokenize(normalizedKeyword);

        return SearchResponseDto.builder()
                .teams(shouldSearch(type, SearchType.TEAM) ? searchTeams(normalizedKeyword) : List.of())
                .players(shouldSearch(type, SearchType.PLAYER) ? searchPlayers(normalizedKeyword) : List.of())
                .fixtures(shouldSearch(type, SearchType.FIXTURE) ? searchFixtures(tokens) : List.of())
                .build();
    }

    private boolean shouldSearch(SearchType requestedType, SearchType targetType) {
        return requestedType == SearchType.ALL || requestedType == targetType;
    }

    private List<SearchResponseDto.TeamResult> searchTeams(String keyword) {
        return teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(keyword).stream()
                .limit(RESULT_LIMIT)
                .map(this::toTeamResult)
                .toList();
    }

    private List<SearchResponseDto.PlayerResult> searchPlayers(String keyword) {
        return playerRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(keyword).stream()
                .limit(RESULT_LIMIT)
                .map(this::toPlayerResult)
                .toList();
    }

    private List<SearchResponseDto.FixtureResult> searchFixtures(List<String> tokens) {
        return fixtureRepository.searchByTeamNameTokens(tokens, RESULT_LIMIT).stream()
                .map(this::toFixtureResult)
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
    }

    private List<String> tokenize(String keyword) {
        return Arrays.stream(keyword.split(" "))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private SearchResponseDto.TeamResult toTeamResult(Team team) {
        return SearchResponseDto.TeamResult.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .code(team.getCode())
                .logoUrl(team.getLogoUrl())
                .build();
    }

    private SearchResponseDto.PlayerResult toPlayerResult(Player player) {
        return SearchResponseDto.PlayerResult.builder()
                .playerId(player.getPlayerId())
                .playerName(player.getName())
                .position(player.getPosition())
                .photoUrl(player.getPhotoUrl())
                .build();
    }

    private SearchResponseDto.FixtureResult toFixtureResult(Fixture fixture) {
        return SearchResponseDto.FixtureResult.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(fixture.getFixtureDate())
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .build();
    }
}
