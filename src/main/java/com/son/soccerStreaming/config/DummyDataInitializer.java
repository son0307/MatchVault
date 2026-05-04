package com.son.soccerStreaming.config;

import com.son.soccerStreaming.entity.*;
import com.son.soccerStreaming.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Profile("local") // 🚨 로컬 환경 보호
@RequiredArgsConstructor
public class DummyDataInitializer implements CommandLineRunner {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final PlayerMatchStatRepository playerMatchStatRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (teamRepository.count() > 0) {
            log.info("더미 데이터가 이미 존재하여 생성을 건너뜁니다.");
            return;
        }

        log.info("🚀 API-Sports 규격 더미 데이터 생성을 시작합니다...");
        Random random = new Random();

        // 1. 5개 팀 생성 (실제 API ID 기반)
        List<Team> teams = createTeams();
        teamRepository.saveAll(teams);

        // 2. 각 팀당 15명의 선수 생성
        Map<Team, List<Player>> teamPlayersMap = new HashMap<>();
        List<Player> allPlayers = new ArrayList<>();
        List<String> positions = Arrays.asList("Attacker", "Midfielder", "Defender", "Goalkeeper");

        long playerIdCounter = 10000L; // API 선수 ID 흉내

        for (Team team : teams) {
            List<Player> playersForTeam = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                String pos = positions.get(random.nextInt(positions.size()));
                // 등번호 1번은 무조건 골키퍼로 설정
                if (i == 1) pos = "Goalkeeper";

                Player player = Player.builder()
                        .apiPlayerId(playerIdCounter++)
                        .name(team.getCode() + " Player " + i)
                        .firstname("Name" + i)
                        .lastname("Surname")
                        .age(random.nextInt(15) + 18)
                        .birthDate(LocalDate.now().minusYears(20).minusDays(random.nextInt(365)))
                        .birthPlace("London")
                        .birthCountry("England")
                        .nationality("England")
                        .height((random.nextInt(30) + 165) + " cm")
                        .weight((random.nextInt(25) + 65) + " kg")
                        .position(pos)
                        .defaultNumber(i)
                        .photoUrl("https://media.api-sports.io/football/players/" + (playerIdCounter - 1) + ".png")
                        .team(team)
                        .build();
                playersForTeam.add(player);
                allPlayers.add(player);
            }
            teamPlayersMap.put(team, playersForTeam);
        }
        playerRepository.saveAll(allPlayers);

        // 3. 7개의 경기와 라인업, 스탯 생성
        long fixtureIdCounter = 1208000L; // API 경기 ID 흉내

        for (int i = 1; i <= 7; i++) {
            Team homeTeam = teams.get(i % 5);
            Team awayTeam = teams.get((i + 1) % 5);

            int homeScore = random.nextInt(4);
            int awayScore = random.nextInt(4);

            MatchRecord match = MatchRecord.builder()
                    .apiFixtureId(fixtureIdCounter++)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .matchDate(LocalDateTime.now().minusDays(random.nextInt(30)))
                    .referee("Michael Oliver")
                    .round("Regular Season - 1")
                    .venueId(homeTeam.getVenue().getVenueApiId())
                    .venueName(homeTeam.getVenue().getVenueName())
                    .venueCity(homeTeam.getVenue().getVenueCity())
                    .statusShort("FT")
                    .statusLong("Match Finished")
                    .matchCategory("FINISHED")
                    .elapsed(90)
                    .homeScore(homeScore)
                    .awayScore(awayScore)
                    .homeFormation("4-3-3")
                    .awayFormation("4-2-3-1")
                    .homeCoachName("Home Coach")
                    .awayCoachName("Away Coach")
                    .build();
            matchRecordRepository.save(match);

            // 해당 경기의 라인업 및 스탯 생성
            generateLineupAndStats(match, homeTeam, teamPlayersMap.get(homeTeam), random);
            generateLineupAndStats(match, awayTeam, teamPlayersMap.get(awayTeam), random);
        }

        log.info("✅ 5개 팀, 75명 선수, 7개 경기 및 통계 더미 데이터 생성 완료!");
    }

    // 💡 새로운 Team 및 임베디드 Venue 엔티티 규격 적용
    private List<Team> createTeams() {
        return Arrays.asList(
                Team.builder().teamApiId(47L).name("Tottenham").code("TOT").country("England").founded(1882)
                        .logoUrl("https://media.api-sports.io/football/teams/47.png")
                        .venue(Venue.builder().venueApiId(593L).venueName("Tottenham Hotspur Stadium").venueAddress("782 High Road").venueCity("London").capacity(62850).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/593.png").build()).build(),
                Team.builder().teamApiId(42L).name("Arsenal").code("ARS").country("England").founded(1886)
                        .logoUrl("https://media.api-sports.io/football/teams/42.png")
                        .venue(Venue.builder().venueApiId(494L).venueName("Emirates Stadium").venueAddress("Hornsey Road").venueCity("London").capacity(60383).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/494.png").build()).build(),
                Team.builder().teamApiId(50L).name("Manchester City").code("MCI").country("England").founded(1880)
                        .logoUrl("https://media.api-sports.io/football/teams/50.png")
                        .venue(Venue.builder().venueApiId(555L).venueName("Etihad Stadium").venueAddress("Rowsley Street").venueCity("Manchester").capacity(55097).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/555.png").build()).build(),
                Team.builder().teamApiId(40L).name("Liverpool").code("LIV").country("England").founded(1892)
                        .logoUrl("https://media.api-sports.io/football/teams/40.png")
                        .venue(Venue.builder().venueApiId(550L).venueName("Anfield").venueAddress("Anfield Road").venueCity("Liverpool").capacity(61276).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/550.png").build()).build(),
                Team.builder().teamApiId(49L).name("Chelsea").code("CHE").country("England").founded(1905)
                        .logoUrl("https://media.api-sports.io/football/teams/49.png")
                        .venue(Venue.builder().venueApiId(519L).venueName("Stamford Bridge").venueAddress("Fulham Road").venueCity("London").capacity(41841).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/519.png").build()).build()
        );
    }

    private void generateLineupAndStats(MatchRecord match, Team team, List<Player> teamPlayers, Random random) {
        // 11명 선발, 4명 교체
        for (int i = 0; i < 15; i++) {
            Player player = teamPlayers.get(i);
            boolean isStarter = i < 11;

            // 포지션 약어 변환 (Attacker -> F, Midfielder -> M 등)
            String posShort = player.getPosition().substring(0, 1);
            // 프론트엔드를 위한 가짜 그리드 생성 (예: "3:2")
            String grid = isStarter ? (random.nextInt(4) + 1) + ":" + (random.nextInt(4) + 1) : null;

            // 1. 👕 라인업 저장
            MatchLineup lineup = MatchLineup.builder()
                    .matchRecord(match)
                    .team(team)
                    .player(player)
                    .jerseyNumber(player.getDefaultNumber())
                    .position(posShort)
                    .grid(grid)
                    .isStarter(isStarter)
                    .build();
            matchLineupRepository.save(lineup);

            // 2. 📊 방대한 경기 스탯 저장
            int passes = random.nextInt(40) + 20;
            PlayerMatchStat stat = PlayerMatchStat.builder()
                    .matchRecord(match)
                    .team(team)
                    .player(player)
                    .minutesPlayed(isStarter ? 90 : random.nextInt(45))
                    .rating(6.0 + (random.nextDouble() * 3.0)) // 6.0 ~ 9.0 평점
                    .isCaptain(i == 1) // 1번 선수를 주장으로
                    .isSubstitute(!isStarter)
                    .goals(random.nextInt(100) > 90 ? 1 : 0) // 10% 확률로 득점
                    .assists(random.nextInt(100) > 90 ? 1 : 0)
                    .shotsTotal(random.nextInt(5))
                    .shotsOnTarget(random.nextInt(3))
                    .passesTotal(passes)
                    .passesKey(random.nextInt(3))
                    .passAccuracy(random.nextInt(30) + 70) // 70~100%
                    .tacklesTotal(random.nextInt(4))
                    .interceptions(random.nextInt(3))
                    .duelsTotal(random.nextInt(10) + 5)
                    .duelsWon(random.nextInt(5))
                    .dribblesAttempts(random.nextInt(5))
                    .dribblesSuccess(random.nextInt(3))
                    .foulsCommitted(random.nextInt(3))
                    .foulsDrawn(random.nextInt(3))
                    .yellowCards(random.nextInt(100) > 85 ? 1 : 0)
                    .redCards(0)
                    .build();
            playerMatchStatRepository.save(stat);
        }
    }
}
