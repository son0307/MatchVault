package com.son.soccerStreaming.dev;

import com.son.soccerStreaming.team.repository.*;

import com.son.soccerStreaming.team.entity.*;

import com.son.soccerStreaming.player.repository.*;

import com.son.soccerStreaming.player.entity.*;

import com.son.soccerStreaming.fixture.repository.*;

import com.son.soccerStreaming.fixture.entity.*;

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
@Profile("local") // ?슚 濡쒖뺄 ?섍꼍 蹂댄샇
@RequiredArgsConstructor
public class DummyDataInitializer implements CommandLineRunner {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRecordRepository fixtureRecordRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;
    private final FixtureEventRepository fixtureEventRepository;

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
        teamStandingRepository.saveAll(createStandings(teams));

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
                        .playerId(playerIdCounter++)
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
                        .number(i)
                        .photoUrl("https://media.api-sports.io/football/players/" + (playerIdCounter - 1) + ".png")
                        .build();
                playersForTeam.add(player);
                allPlayers.add(player);
            }
            teamPlayersMap.put(team, playersForTeam);
        }
        playerRepository.saveAll(allPlayers);
        playerTeamSeasonStatRepository.saveAll(createPlayerTeamSeasonStats(teamPlayersMap));

        // 3. 7개의 경기와 라인업, 스탯 생성
        long fixtureIdCounter = 1208000L; // API 경기 ID 흉내

        for (int i = 1; i <= 7; i++) {
            Team homeTeam = teams.get(i % 5);
            Team awayTeam = teams.get((i + 1) % 5);

            int homeScore = random.nextInt(4);
            int awayScore = random.nextInt(4);

            Fixture fixture = Fixture.builder()
                    .fixtureId(fixtureIdCounter++)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .fixtureDate(LocalDateTime.now().minusDays(random.nextInt(30)))
                    .referee("Michael Oliver")
                    .round(1)
                    .venueId(homeTeam.getVenue().getVenueId())
                    .venueName(homeTeam.getVenue().getVenueName())
                    .venueCity(homeTeam.getVenue().getVenueCity())
                    .statusShort("FT")
                    .statusLong("Match Finished")
                    .fixtureStatus("FINISHED")
                    .elapsed(90)
                    .homeScore(homeScore)
                    .awayScore(awayScore)
                    .homeFormation("4-3-3")
                    .awayFormation("4-2-3-1")
                    .homeCoachName("Home Coach")
                    .awayCoachName("Away Coach")
                    .build();
            fixtureRecordRepository.save(fixture);

            // 해당 경기의 라인업 및 스탯 생성
            generateLineupAndStats(fixture, homeTeam, teamPlayersMap.get(homeTeam), random);
            generateLineupAndStats(fixture, awayTeam, teamPlayersMap.get(awayTeam), random);
            generateAbsences(fixture, homeTeam, awayTeam, teamPlayersMap, random);
            generateEvents(fixture, homeTeam, awayTeam, teamPlayersMap, random);
        }

        log.info("✅ 5개 팀, 75명 선수, 7개 경기 및 통계 더미 데이터 생성 완료!");
    }

    // 💡 새로운 Team 및 임베디드 Venue 엔티티 규격 적용
    private List<Team> createTeams() {
        return Arrays.asList(
                Team.builder().teamId(47L).name("Tottenham").code("TOT").country("England").founded(1882)
                        .logoUrl("https://media.api-sports.io/football/teams/47.png")
                        .venue(Venue.builder().venueId(593L).venueName("Tottenham Hotspur Stadium").venueAddress("782 High Road").venueCity("London").capacity(62850).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/593.png").build()).build(),
                Team.builder().teamId(42L).name("Arsenal").code("ARS").country("England").founded(1886)
                        .logoUrl("https://media.api-sports.io/football/teams/42.png")
                        .venue(Venue.builder().venueId(494L).venueName("Emirates Stadium").venueAddress("Hornsey Road").venueCity("London").capacity(60383).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/494.png").build()).build(),
                Team.builder().teamId(50L).name("Manchester City").code("MCI").country("England").founded(1880)
                        .logoUrl("https://media.api-sports.io/football/teams/50.png")
                        .venue(Venue.builder().venueId(555L).venueName("Etihad Stadium").venueAddress("Rowsley Street").venueCity("Manchester").capacity(55097).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/555.png").build()).build(),
                Team.builder().teamId(40L).name("Liverpool").code("LIV").country("England").founded(1892)
                        .logoUrl("https://media.api-sports.io/football/teams/40.png")
                        .venue(Venue.builder().venueId(550L).venueName("Anfield").venueAddress("Anfield Road").venueCity("Liverpool").capacity(61276).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/550.png").build()).build(),
                Team.builder().teamId(49L).name("Chelsea").code("CHE").country("England").founded(1905)
                        .logoUrl("https://media.api-sports.io/football/teams/49.png")
                        .venue(Venue.builder().venueId(519L).venueName("Stamford Bridge").venueAddress("Fulham Road").venueCity("London").capacity(41841).surface("grass").venueImageUrl("https://media.api-sports.io/football/venues/519.png").build()).build()
        );
    }

    private List<TeamStanding> createStandings(List<Team> teams) {
        List<TeamStanding> standings = new ArrayList<>();
        int[][] records = {
                {1, 70, 41, 24, 23, 1, 0, 56, 15, 12, 12, 0, 0, 31, 9, 12, 11, 1, 0, 25, 6},
                {2, 51, 36, 24, 16, 3, 5, 65, 29, 12, 8, 2, 2, 35, 14, 12, 8, 1, 3, 30, 15},
                {3, 45, 18, 24, 13, 6, 5, 42, 24, 12, 7, 3, 2, 22, 11, 12, 6, 3, 3, 20, 13},
                {4, 40, 10, 24, 11, 7, 6, 38, 28, 12, 6, 4, 2, 21, 12, 12, 5, 3, 4, 17, 16},
                {5, 36, 6, 24, 10, 6, 8, 35, 29, 12, 6, 2, 4, 19, 13, 12, 4, 4, 4, 16, 16}
        };
        String[] forms = {"WWWWW", "WLWWW", "DWWLW", "LDWWW", "WLDLW"};

        for (int i = 0; i < teams.size(); i++) {
            int[] record = records[i % records.length];
            standings.add(TeamStanding.builder()
                    .team(teams.get(i))
                    .season(2019)
                    .rank(record[0])
                    .points(record[1])
                    .goalsDiff(record[2])
                    .group("Premier League")
                    .form(forms[i % forms.length])
                    .status("same")
                    .description(record[0] <= 4 ? "Promotion - Champions League (Group Stage)" : null)
                    .played(record[3])
                    .win(record[4])
                    .draw(record[5])
                    .lose(record[6])
                    .goalsFor(record[7])
                    .goalsAgainst(record[8])
                    .homePlayed(record[9])
                    .homeWin(record[10])
                    .homeDraw(record[11])
                    .homeLose(record[12])
                    .homeGoalsFor(record[13])
                    .homeGoalsAgainst(record[14])
                    .awayPlayed(record[15])
                    .awayWin(record[16])
                    .awayDraw(record[17])
                    .awayLose(record[18])
                    .awayGoalsFor(record[19])
                    .awayGoalsAgainst(record[20])
                    .apiUpdatedAt(LocalDateTime.of(2020, 1, 29, 0, 0))
                    .build());
        }

        return standings;
    }

    private List<PlayerTeamSeasonStat> createPlayerTeamSeasonStats(Map<Team, List<Player>> teamPlayersMap) {
        List<PlayerTeamSeasonStat> stats = new ArrayList<>();
        for (Map.Entry<Team, List<Player>> entry : teamPlayersMap.entrySet()) {
            Team team = entry.getKey();
            for (Player player : entry.getValue()) {
                PlayerTeamSeasonStat stat = PlayerTeamSeasonStat.builder()
                        .player(player)
                        .team(team)
                        .leagueId(39L)
                        .season(2019)
                        .build();

                stat.updateSeasonStat(
                        player.getNumber(),
                        player.getPosition(),
                        24,
                        18,
                        1600,
                        6.8,
                        false,
                        3,
                        4,
                        6,
                        20,
                        8,
                        2,
                        0,
                        1,
                        0,
                        500,
                        18,
                        82,
                        24,
                        5,
                        18,
                        70,
                        41,
                        26,
                        14,
                        9,
                        20,
                        16,
                        3,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                );
                stats.add(stat);
            }
        }
        return stats;
    }

    private void generateLineupAndStats(Fixture fixture, Team team, List<Player> teamPlayers, Random random) {
        // 11명 선발, 4명 교체
        for (int i = 0; i < 15; i++) {
            Player player = teamPlayers.get(i);
            boolean isStarter = i < 11;

            // 포지션 약어 변환 (Attacker -> F, Midfielder -> M 등)
            String posShort = player.getPosition().substring(0, 1);
            // 프론트엔드를 위한 가짜 그리드 생성 (예: "3:2")
            String grid = isStarter ? (random.nextInt(4) + 1) + ":" + (random.nextInt(4) + 1) : null;

            // 1. 👕 라인업 저장
            FixtureLineup lineup = FixtureLineup.builder()
                    .fixture(fixture)
                    .team(team)
                    .player(player)
                    .jerseyNumber(player.getNumber())
                    .position(posShort)
                    .grid(grid)
                    .isStarter(isStarter)
                    .build();
            fixtureLineupRepository.save(lineup);

            // 2. 📊 방대한 경기 스탯 저장
            int passes = random.nextInt(40) + 20;
            int passAccuracy = random.nextInt(30) + 70;
            int passesAccurate = (int) Math.round(passes * (passAccuracy / 100.0));
            PlayerFixtureStat stat = PlayerFixtureStat.builder()
                    .fixture(fixture)
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
                    .passesAccurate(passesAccurate)
                    .passAccuracy(passAccuracy) // 70~100%
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
            playerFixtureStatRepository.save(stat);
        }
    }

    private void generateAbsences(Fixture fixture, Team homeTeam, Team awayTeam,
                                  Map<Team, List<Player>> teamPlayersMap, Random random) {
        List<PlayerAbsence> absences = new ArrayList<>();
        absences.add(PlayerAbsence.builder()
                .fixture(fixture)
                .team(homeTeam)
                .player(teamPlayersMap.get(homeTeam).get(13))
                .absenceType("Missing Fixture")
                .reason(random.nextBoolean() ? "Suspended" : "Hamstring Injury")
                .build());
        absences.add(PlayerAbsence.builder()
                .fixture(fixture)
                .team(awayTeam)
                .player(teamPlayersMap.get(awayTeam).get(14))
                .absenceType("Questionable")
                .reason(random.nextBoolean() ? "Knock" : "Illness")
                .build());

        playerAbsenceRepository.saveAll(absences);
    }

    private void generateEvents(Fixture fixture, Team homeTeam, Team awayTeam,
                                Map<Team, List<Player>> teamPlayersMap, Random random) {
        List<Player> homePlayers = teamPlayersMap.get(homeTeam);
        List<Player> awayPlayers = teamPlayersMap.get(awayTeam);
        List<FixtureEvent> events = new ArrayList<>();
        int sequence = 1;

        events.add(FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(sequence++)
                .elapsed(12)
                .team(homeTeam)
                .player(homePlayers.get(8))
                .assistPlayer(homePlayers.get(5))
                .eventType("Goal")
                .eventDetail("Normal Goal")
                .build());
        events.add(FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(sequence++)
                .elapsed(27)
                .team(awayTeam)
                .player(awayPlayers.get(6))
                .eventType("Card")
                .eventDetail("Yellow Card")
                .comments("Roughing")
                .build());
        events.add(FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(sequence++)
                .elapsed(45)
                .extra(2)
                .team(awayTeam)
                .player(awayPlayers.get(9))
                .assistPlayer(awayPlayers.get(7))
                .eventType("Goal")
                .eventDetail(random.nextBoolean() ? "Normal Goal" : "Penalty")
                .build());
        events.add(FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(sequence++)
                .elapsed(62)
                .team(homeTeam)
                .player(homePlayers.get(12))
                .assistPlayer(homePlayers.get(4))
                .eventType("subst")
                .eventDetail("Substitution 1")
                .comments("Tactical change")
                .build());
        events.add(FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(sequence)
                .elapsed(78)
                .team(homeTeam)
                .player(homePlayers.get(3))
                .eventType("Card")
                .eventDetail("Yellow Card")
                .build());

        fixtureEventRepository.saveAll(events);
    }
}
