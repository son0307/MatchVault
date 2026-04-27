package com.son.soccerStreaming.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.son.soccerStreaming.entity.MatchRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.son.soccerStreaming.entity.QMatchRecord.matchRecord;

@Repository
@RequiredArgsConstructor
public class MatchRecordRepositoryImpl implements MatchRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MatchRecord> findRecentMatchesWithCursor(Long cursorId, int size) {
        return queryFactory
                .selectFrom(matchRecord)
                .where(
                        ltCursorId(cursorId)    // null이면 무시, 아니면 조건 사용
                )
                .orderBy(matchRecord.id.desc())
                .limit(size + 1)    // 프론트가 요청한 것 보다 1개 더 조회 (다음 페이지 검사)
                .fetch();
    }

    private BooleanExpression ltCursorId(Long cursorId) {
        if (cursorId == null) {
            // 커서 없음 -> 첫 페이지
            return null;
        }

        // 커서 있음 -> 더 작은 ID(과거) 데이터만 조회
        return matchRecord.id.lt(cursorId);
    }
}
