package com.son.soccerStreaming.search.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.search.dto.SearchResponseDto;
import com.son.soccerStreaming.search.dto.SearchType;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private FixtureRecordRepository fixtureRecordRepository;

    @InjectMocks
    private SearchService searchService;

    @Test
    void searchReturnsMatchingTeamsPlayersAndFixtures() {
        Team team = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .code("TOT")
                .logoUrl("tottenham.png")
                .build();
        Player player = Player.builder()
                .playerId(1L)
                .name("Son Heung-min")
                .position("Attacker")
                .photoUrl("son.png")
                .build();
        Fixture fixture = fixture(100L, team, team);

        when(teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("tottenham"))
                .thenReturn(List.of(team));
        when(playerRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("tottenham"))
                .thenReturn(List.of(player));
        when(fixtureRecordRepository.searchByTeamNameTokens(List.of("tottenham"), 10))
                .thenReturn(List.of(fixture));

        SearchResponseDto response = searchService.search("tottenham");

        assertThat(response.getTeams()).extracting(SearchResponseDto.TeamResult::getTeamName)
                .containsExactly("Tottenham");
        assertThat(response.getPlayers()).extracting(SearchResponseDto.PlayerResult::getPlayerName)
                .containsExactly("Son Heung-min");
        assertThat(response.getFixtures()).extracting(SearchResponseDto.FixtureResult::getFixtureId)
                .containsExactly(100L);
    }

    @Test
    void searchTokenizesMultipleWordsForFixtureSearch() {
        Team tottenham = Team.builder().teamId(47L).name("Tottenham").build();
        Team chelsea = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = fixture(200L, tottenham, chelsea);

        when(teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("tottenham chelsea"))
                .thenReturn(List.of());
        when(playerRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("tottenham chelsea"))
                .thenReturn(List.of());
        when(fixtureRecordRepository.searchByTeamNameTokens(List.of("tottenham", "chelsea"), 10))
                .thenReturn(List.of(fixture));

        SearchResponseDto response = searchService.search("  tottenham   chelsea  ");

        assertThat(response.getFixtures()).extracting(SearchResponseDto.FixtureResult::getFixtureId)
                .containsExactly(200L);
        ArgumentCaptor<List<String>> tokensCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixtureRecordRepository).searchByTeamNameTokens(tokensCaptor.capture(), org.mockito.ArgumentMatchers.eq(10));
        assertThat(tokensCaptor.getValue()).containsExactly("tottenham", "chelsea");
    }

    @Test
    void searchLimitsTeamsAndPlayersToTenResults() {
        List<Team> teams = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> Team.builder().teamId((long) index).name("Team " + index).build())
                .toList();
        List<Player> players = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> Player.builder().playerId((long) index).name("Player " + index).build())
                .toList();

        when(teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("a")).thenReturn(teams);
        when(playerRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("a")).thenReturn(players);
        when(fixtureRecordRepository.searchByTeamNameTokens(List.of("a"), 10)).thenReturn(List.of());

        SearchResponseDto response = searchService.search("a");

        assertThat(response.getTeams()).hasSize(10);
        assertThat(response.getPlayers()).hasSize(10);
    }

    @Test
    void teamSearchOnlyReturnsTeams() {
        Team team = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .build();

        when(teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc("tottenham"))
                .thenReturn(List.of(team));

        SearchResponseDto response = searchService.search("tottenham", SearchType.TEAM);

        assertThat(response.getTeams()).hasSize(1);
        assertThat(response.getPlayers()).isEmpty();
        assertThat(response.getFixtures()).isEmpty();
        verify(playerRepository, never()).findTop20ByNameContainingIgnoreCaseOrderByNameAsc(org.mockito.ArgumentMatchers.anyString());
        verify(fixtureRecordRepository, never()).searchByTeamNameTokens(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void fixtureSearchOnlyReturnsFixtures() {
        Team tottenham = Team.builder().teamId(47L).name("Tottenham").build();
        Team chelsea = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = fixture(200L, tottenham, chelsea);

        when(fixtureRecordRepository.searchByTeamNameTokens(List.of("tottenham", "chelsea"), 10))
                .thenReturn(List.of(fixture));

        SearchResponseDto response = searchService.search("tottenham chelsea", SearchType.FIXTURE);

        assertThat(response.getTeams()).isEmpty();
        assertThat(response.getPlayers()).isEmpty();
        assertThat(response.getFixtures()).extracting(SearchResponseDto.FixtureResult::getFixtureId)
                .containsExactly(200L);
        verify(teamRepository, never()).findTop20ByNameContainingIgnoreCaseOrderByNameAsc(org.mockito.ArgumentMatchers.anyString());
        verify(playerRepository, never()).findTop20ByNameContainingIgnoreCaseOrderByNameAsc(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blankSearchReturnsEmptyResultsWithoutRepositoryCalls() {
        SearchResponseDto response = searchService.search("   ");

        assertThat(response.getTeams()).isEmpty();
        assertThat(response.getPlayers()).isEmpty();
        assertThat(response.getFixtures()).isEmpty();
        verify(teamRepository, never()).findTop20ByNameContainingIgnoreCaseOrderByNameAsc(org.mockito.ArgumentMatchers.anyString());
        verify(playerRepository, never()).findTop20ByNameContainingIgnoreCaseOrderByNameAsc(org.mockito.ArgumentMatchers.anyString());
        verify(fixtureRecordRepository, never()).searchByTeamNameTokens(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    private Fixture fixture(Long fixtureId, Team homeTeam, Team awayTeam) {
        return Fixture.builder()
                .fixtureId(fixtureId)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fixtureStatus("FT")
                .homeScore(2)
                .awayScore(1)
                .build();
    }
}
