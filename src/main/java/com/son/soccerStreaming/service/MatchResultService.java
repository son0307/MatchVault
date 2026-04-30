package com.son.soccerStreaming.service;

import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.entity.Player;
import com.son.soccerStreaming.entity.PlayerMatchStat;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import com.son.soccerStreaming.repository.PlayerMatchStatRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchResultService {

    private final StringRedisTemplate redisTemplate;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerRepository playerRepository;
    private final PlayerMatchStatRepository matchStatRepository;
    private final RedisCacheManager cacheManager;

    @CacheEvict(value = "recentMatches", allEntries = true)
    @Transactional
    public void closeMatch(String matchId) {
        // 경기 정보 조회
        MatchRecord match = matchRecordRepository.findByMatchId(matchId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        // Redis에 기록된 스코어 합산
        int homeScore = getCountFromRedis(matchId, "GOAL", match.getHomeTeam().getTeamId());
        int awayScore = getCountFromRedis(matchId,"GOAL", match.getAwayTeam().getTeamId());

        // 경기 결과 업데이트
        match.finishMatch(homeScore, awayScore);

        // 선수별 상세 스탯 업데이트
        savePlayerStatsToDb(match, matchId);

        // Redis 데이터 정리
        clearMatchDataFromRedis(matchId);

        log.info("✅ [{}] 경기 결과 및 선수 스탯 DB 저장 완료!", matchId);
    }

    private void savePlayerStatsToDb(MatchRecord match, String matchId) {
        String pattern = "match:" + matchId + ":player:*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) return;

        List<PlayerMatchStat> statsToSave = new ArrayList<>();

        for (String key : keys) {
            // Redis Hash에서 해당 선수의 모든 필드를 가져옴
            var entries = redisTemplate.opsForHash().entries(key);

            // 키에서 선수 ID 추출
            String playerId = key.split(":player:")[1];
            System.out.println(playerId);

            // 선수 엔티티 조회
            Player player = playerRepository.findByPlayerId(playerId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));

            PlayerMatchStat stat = PlayerMatchStat.builder()
                    .matchRecord(match)
                    .player(player)
                    .goals(parseStat(entries.get("goals")))
                    .shots(parseStat(entries.get("totalShots")))
                    .shotsOnTarget(parseStat(entries.get("shotsOnTarget")))
                    .totalPasses(parseStat(entries.get("totalPasses")))
                    .successfulPasses(parseStat(entries.get("successfulPasses")))
                    .tackles(parseStat(entries.get("tackles")))
                    .fouls(parseStat(entries.get("fouls")))
                    .build();

            statsToSave.add(stat);

            // 이번 경기를 뛴 선수의 누적 스탯 캐시 무효화 (새로 계산 필요)
            var cache = cacheManager.getCache("playerStats");
            if (cache != null)
                cache.evict(playerId);
        }

        matchStatRepository.saveAll(statsToSave);
    }

    private int getCountFromRedis(String matchId, String eventType, String teamId) {
        String key = "match:" + matchId + ":count:" + eventType + ":" + teamId;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }

    private int parseStat(Object value) {
        if (value == null) return 0;
        return Integer.parseInt(value.toString());
    }

    private void clearMatchDataFromRedis(String matchId) {
        // 해당 경기와 관련된 모든 데이터 삭제
        Set<String> keys = redisTemplate.keys("match:*:" + matchId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
