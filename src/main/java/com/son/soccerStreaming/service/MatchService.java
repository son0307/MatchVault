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
                        .homeScore(valueOf(match.getHomeScore()))
                        .awayScore(valueOf(match.getAwayScore()))
                        .matchStatus(match.getMatchCategory())
                        .build())
                .toList();

        Long nextCursor = hasNext ? matchRecords.get(matchRecords.size() - 1).getId() : null;

        return CursorResponse.<MatchResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
