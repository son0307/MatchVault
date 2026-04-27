package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordRepository matchRecordRepository;

    public CursorResponse<MatchResponseDto.Summary> getRecentMatches(Long cursorId, int size) {

        // Querydsl 메서드 호출
        List<MatchRecord> matchRecords = matchRecordRepository.findRecentMatchesWithCursor(cursorId, size);

        // 다음 페이지 확인 및 잘라내기
        boolean hasNext = false;
        if (matchRecords.size() > size) {
            hasNext = true;
            matchRecords.remove(matchRecords.size() - 1);
        }

        // DTO 변환
        List<MatchResponseDto.Summary> content = matchRecords.stream()
                .map(match -> MatchResponseDto.Summary.builder()
                        .matchId(match.getMatchId())
                        .homeTeamName(match.getHomeTeam().getName())
                        .awayTeamName(match.getAwayTeam().getName())
                        .homeScore(match.getHomeScore())
                        .awayScore(match.getAwayScore())
                        .matchStatus(match.getStatus())
                        .build())
                .toList();

        // 다음 커서 ID 설정
        Long nextCursor = hasNext ? matchRecords.get(matchRecords.size() - 1).getId() : null;

        return CursorResponse.<MatchResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }
}
