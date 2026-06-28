package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixturePlayerStatServiceTest {

    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private FixtureLineupRepository fixtureLineupRepository;

    @InjectMocks
    private FixturePlayerStatService fixturePlayerStatService;

    @Test
    void getFixturePlayerStatsUsesLineupJerseyNumberBeforePlayerDefaultNumber() {
        Team tottenham = team(1L, 47L, "Tottenham");
        Team arsenal = team(2L, 42L, "Arsenal");
        Fixture fixture = Fixture.builder()
                .fixtureId(100L)
                .fixtureDate(LocalDateTime.of(2026, 1, 1, 12, 0))
                .homeTeam(tottenham)
                .awayTeam(arsenal)
                .build();
        Player brennanJohnson = Player.builder()
                .playerId(14L)
                .name("Brennan Johnson")
                .number(22)
                .position("Attacker")
                .build();
        Player otherPlayer = Player.builder()
                .playerId(15L)
                .name("Other Player")
                .number(10)
                .position("Midfielder")
                .build();
        Player awayPlayer = Player.builder()
                .playerId(16L)
                .name("Away Player")
                .number(7)
                .position("Attacker")
                .build();

        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixture));
        when(playerFixtureStatRepository.findAllByFixtureFixtureId(100L)).thenReturn(List.of(
                stat(fixture, tottenham, brennanJohnson),
                stat(fixture, tottenham, otherPlayer),
                stat(fixture, arsenal, awayPlayer)
        ));
        when(fixtureLineupRepository.findAllByFixtureId(100L)).thenReturn(List.of(
                lineup(fixture, tottenham, brennanJohnson, 11),
                lineup(fixture, arsenal, awayPlayer, 7)
        ));

        FixturePlayerStatResponseDto response = fixturePlayerStatService.getFixturePlayerStats(100L);

        assertThat(response.getHomeTeam().getPlayers())
                .extracting(FixturePlayerStatResponseDto.PlayerStat::getPlayerName)
                .containsExactly("Other Player", "Brennan Johnson");
        assertThat(response.getHomeTeam().getPlayers())
                .extracting(FixturePlayerStatResponseDto.PlayerStat::getJerseyNumber)
                .containsExactly(10, 11);
        assertThat(response.getAwayTeam().getPlayers())
                .singleElement()
                .extracting(FixturePlayerStatResponseDto.PlayerStat::getJerseyNumber)
                .isEqualTo(7);
    }

    private PlayerFixtureStat stat(Fixture fixture, Team team, Player player) {
        return PlayerFixtureStat.builder()
                .fixture(fixture)
                .team(team)
                .player(player)
                .minutesPlayed(90)
                .build();
    }

    private FixtureLineup lineup(Fixture fixture, Team team, Player player, Integer number) {
        return FixtureLineup.builder()
                .fixture(fixture)
                .team(team)
                .player(player)
                .jerseyNumber(number)
                .position(player.getPosition())
                .isStarter(true)
                .build();
    }

    private Team team(Long id, Long teamId, String name) {
        return Team.builder()
                .id(id)
                .teamId(teamId)
                .name(name)
                .build();
    }
}
