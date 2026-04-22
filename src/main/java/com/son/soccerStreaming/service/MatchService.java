package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.entity.MatchRecord;
import com.son.soccerStreaming.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordRepository matchRecordRepository;

    public CursorResponse<MatchResponseDto.Summary> getMatches(Long cursorId, int size) {
        // 클라이언트가 요청한 size 보다 1개 더 많이 조회
        Pageable pageable = PageRequest.of(0, size + 1);

        List<MatchRecord> matchRecords;

        // 클라이언트가 커서 ID를 포함했냐 안했냐에 따라 동적으로 호출
        if (cursorId == null) {
            matchRecords = matchRecordRepository.findAllByOrderByIdDesc(pageable);
        } else {
            matchRecords = matchRecordRepository.findByIdLessThanOrderByIdDesc(cursorId, pageable);
        }

        // hasNext 판별
        boolean hasNext = false;
        if (matchRecords.size() > size) {
            hasNext = true;
            // 클라이언트 응답 시 마지막 확인용 1개는 잘라내기
            matchRecords = matchRecords.subList(0, size);
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

        // 다음 커서 추출
        Long nextCursor = content.isEmpty() ? null : matchRecords.get(content.size() - 1).getId();

        return CursorResponse.<MatchResponseDto.Summary>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}
