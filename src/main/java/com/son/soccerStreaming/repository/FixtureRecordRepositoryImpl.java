package com.son.soccerStreaming.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.son.soccerStreaming.entity.Fixture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.son.soccerStreaming.entity.QFixture.fixture;

@Repository
@RequiredArgsConstructor
public class FixtureRecordRepositoryImpl implements FixtureRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season, int size) {
        return queryFactory
                .selectFrom(fixture)
                .where(
                        ltCursorId(cursorId),
                        eqSeason(season)
                )
                .orderBy(fixture.id.desc())
                .limit(size + 1)
                .fetch();
    }

    private BooleanExpression ltCursorId(Long cursorId) {
        return cursorId == null ? null : fixture.id.lt(cursorId);
    }

    private BooleanExpression eqSeason(Integer season) {
        return season == null ? null : fixture.season.eq(season);
    }
}
