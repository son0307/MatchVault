package com.son.soccerStreaming.config;

import com.son.soccerStreaming.entity.*;
import com.son.soccerStreaming.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@Profile("local") // 🚨 로컬 환경에서만 실행되도록 보호 (운영 DB에 더미가 들어가는 대참사 방지)
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
        // 이미 데이터가 있다면 생성하지 않음
        if (teamRepository.count() > 0) {
            log.info("더미 데이터가 이미 존재하여 생성을 건너뜁니다.");
            return;
        }

        log.info("🚀 더미 데이터 생성을 시작합니다...");
        Random random = new Random();

        // 1. 5개 팀 생성
        List<Team> teams = createTeams();
        teamRepository.saveAll(teams);

        // 2. 각 팀당 15명의 선수 생성
        List<Player> allPlayers = new ArrayList<>();
        List<String> positions = Arrays.asList("ST", "LW", "RW", "AM", "CM", "DM", "LB", "CB", "RB", "GK");

        for (Team team : teams) {
            for (int i = 1; i <= 15; i++) {
                Player player = Player.builder()
                        .playerId("player_" + team.getTeamId().split("_")[1] + "_" + i)
                        .name(team.getName() + " Player " + i)
                        .age(random.nextInt(15) + 18) // 18~32세
                        .height(random.nextInt(30) + 165) // 165~194cm
                        .weight(random.nextInt(25) + 65) // 65~89kg
                        .backNumber(i)
                        .mainPosition(positions.get(random.nextInt(positions.size())))
                        .team(team)
                        .isDeleted(false)
                        .build();
                allPlayers.add(player);
            }
        }
        playerRepository.saveAll(allPlayers);

        // 3. 7개의 경기와 라인업, 스탯 생성
        for (int i = 1; i <= 7; i++) {
            // 임의로 홈/어웨이 팀 배정 (같은 팀이 되지 않도록)
            Team homeTeam = teams.get(i % 5);
            Team awayTeam = teams.get((i + 1) % 5);

            int homeScore = random.nextInt(4);
            int awayScore = random.nextInt(4);

            MatchRecord match = MatchRecord.builder()
                    .matchId("match_00" + i)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .matchDate(LocalDateTime.now().minusDays(random.nextInt(30))) // 최근 30일 내 경기
                    .status(MatchStatus.FINISHED)
                    .homeScore(homeScore)
                    .awayScore(awayScore)
                    .build();
            matchRecordRepository.save(match);

            // 해당 경기의 라인업 및 스탯 생성 (홈 11명, 어웨이 11명)
            generateLineupAndStats(match, homeTeam, true, random);
            generateLineupAndStats(match, awayTeam, false, random);
        }

        log.info("✅ 5개 팀, 75명 선수, 7개 경기 및 통계 데이터 생성 완료!");
    }

    private List<Team> createTeams() {
        return Arrays.asList(
                Team.builder().teamId("team_forge").name("Forge Albion FC").stadium("Forge Stadium").build(),
                Team.builder().teamId("team_tottenham").name("Tottenham Hotspur").stadium("Spurs Stadium").build(),
                Team.builder().teamId("team_arsenal").name("Arsenal FC").stadium("Emirates").build(),
                Team.builder().teamId("team_mancity").name("Manchester City").stadium("Etihad").build(),
                Team.builder().teamId("team_liverpool").name("Liverpool FC").stadium("Anfield").build()
        );
    }

    private void generateLineupAndStats(MatchRecord match, Team team, boolean isHome, Random random) {
        // 해당 팀의 선수 15명 중 11명을 선발로 추출
        List<Player> teamPlayers = playerRepository.findAllByTeamTeamId(team.getTeamId());

        for (int i = 0; i < 11; i++) {
            Player player = teamPlayers.get(i);

            // 라인업 저장
            MatchLineup lineup = MatchLineup.builder()
                    .matchRecord(match)
                    .player(player)
                    .isStarting(true)
                    .formationPosition(player.getMainPosition()) // 주 포지션을 그대로 포메이션 위치로 사용
                    .build();
            matchLineupRepository.save(lineup);

            // 경기 스탯 저장 (그럴싸한 랜덤 수치)
            PlayerMatchStat stat = PlayerMatchStat.builder()
                    .matchRecord(match)
                    .player(player)
                    .goals(random.nextInt(100) > 90 ? 1 : 0) // 10% 확률로 1골
                    .assists(random.nextInt(100) > 90 ? 1 : 0)
                    .shots(random.nextInt(5))
                    .totalPasses(random.nextInt(40) + 20) // 20~59개 패스
                    .successfulPasses(random.nextInt(20) + 15)
                    .tackles(random.nextInt(4))
                    .fouls(random.nextInt(3))
                    .build();
            playerMatchStatRepository.save(stat);
        }
    }
}