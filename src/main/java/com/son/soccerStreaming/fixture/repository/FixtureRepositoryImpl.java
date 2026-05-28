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
public class FixtureRepositoryImpl implements FixtureRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season, Integer round,
                                                       LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                       Long teamId, int size) {
        return queryFactory
                .selectFrom(fixture)
                .join(fixture.homeTeam).fetchJoin()
                .join(fixture.awayTeam).fetchJoin()
                .where(
                        ltCursorId(cursorId),
                        eqSeason(season),
                        eqRound(round),
                        goeFixtureDate(startDateTime),
                        ltFixtureDate(endDateTime),
                        eqTeam(teamId)
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

    private BooleanExpression eqRound(Integer round) {
        return round == null ? null : fixture.round.eq(round);
    }

    private BooleanExpression goeFixtureDate(LocalDateTime startDateTime) {
        return startDateTime == null ? null : fixture.fixtureDate.goe(startDateTime);
    }

    private BooleanExpression ltFixtureDate(LocalDateTime endDateTime) {
        return endDateTime == null ? null : fixture.fixtureDate.lt(endDateTime);
    }

    private BooleanExpression eqTeam(Long teamId) {
        return teamId == null ? null : fixture.homeTeam.teamId.eq(teamId).or(fixture.awayTeam.teamId.eq(teamId));
    }

    private BooleanExpression containsHomeOrAwayTeamName(String token) {
        return fixture.homeTeam.name.containsIgnoreCase(token)
                .or(fixture.awayTeam.name.containsIgnoreCase(token));
    }
}
