package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordRepository matchRecordRepository;

    @Transactional(readOnly = true)
    public CursorResponse<MatchResponseDto.Summary> getRecentMatches(Long cursorId, int size) {

        List<MatchRecord> matchRecords = matchRecordRepository.findRecentMatchesWithCursor(cursorId, size);

        boolean hasNext = false;
        if (matchRecords.size() > size) {
            hasNext = true;
            matchRecords.remove(matchRecords.size() - 1);
        }

        List<MatchResponseDto.Summary> content = matchRecords.stream()
                .map(match -> MatchResponseDto.Summary.builder()
                        // 💡 DTO의 필드 타입도 Long으로 수정되어야 합니다. (이름도 fixtureId로 변경 권장)
                        .matchId(match.getApiFixtureId())
                        .homeTeamName(match.getHomeTeam().getName())
                        .awayTeamName(match.getAwayTeam().getName())
                        .homeScore(match.getHomeScore())
                        .awayScore(match.getAwayScore())
                        .matchStatus(match.getStatus())
                        .build())
                .toList();

        Long nextCursor = hasNext ? matchRecords.get(matchRecords.size() - 1).getId() : null;

        return CursorResponse.<MatchResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    @Transactional
    // 💡 파라미터 타입을 String -> Long 으로 완벽하게 수정
    public void addScoreToDb(Long fixtureId, Long teamId) {

        // 💡 JPA Repository 메서드명도 변경된 엔티티 필드명에 맞춰 수정 (findByApiFixtureId)
        MatchRecord match = matchRecordRepository.findByApiFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));

        // 💡 Team 엔티티의 고유 ID 필드명(apiId)으로 비교
        if (match.getHomeTeam().getTeamApiId().equals(teamId)) {
            match.addHomeScore();
        } else if (match.getAwayTeam().getTeamApiId().equals(teamId)) {
            match.addAwayScore();
        }
    }
}