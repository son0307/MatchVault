package com.son.soccerStreaming.fixture.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.son.soccerStreaming.fixture.entity.Fixture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.son.soccerStreaming.fixture.entity.QFixture.fixture;

@Repository
@RequiredArgsConstructor
public class FixtureRecordRepositoryImpl implements FixtureRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season,
                                                       LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                       int size) {
        return queryFactory
                .selectFrom(fixture)
                .where(
                        ltCursorId(cursorId),
                        eqSeason(season),
                        goeFixtureDate(startDateTime),
                        ltFixtureDate(endDateTime)
                )
                .orderBy(fixture.id.desc())
                .limit(size + 1)
                .fetch();
    }

    @Override
    public List<Fixture> searchByTeamNameTokens(List<String> tokens, int size) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(fixture)
                .join(fixture.homeTeam).fetchJoin()
                .join(fixture.awayTeam).fetchJoin()
                .where(tokens.stream()
                        .map(this::containsHomeOrAwayTeamName)
                        .reduce(Expressions.TRUE, BooleanExpression::and))
                .orderBy(fixture.fixtureDate.desc())
                .limit(size)
                .fetch();
    }

    private BooleanExpression ltCursorId(Long cursorId) {
        return cursorId == null ? null : fixture.id.lt(cursorId);
    }

    private BooleanExpression eqSeason(Integer season) {
        return season == null ? null : fixture.season.eq(season);
    }

    private BooleanExpression goeFixtureDate(LocalDateTime startDateTime) {
        return startDateTime == null ? null : fixture.fixtureDate.goe(startDateTime);
    }

    private BooleanExpression ltFixtureDate(LocalDateTime endDateTime) {
        return endDateTime == null ? null : fixture.fixtureDate.lt(endDateTime);
    }

    private BooleanExpression containsHomeOrAwayTeamName(String token) {
        return fixture.homeTeam.name.containsIgnoreCase(token)
                .or(fixture.awayTeam.name.containsIgnoreCase(token));
    }
}
