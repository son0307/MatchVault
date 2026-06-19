package com.son.soccerStreaming.fixture.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerFixtureStatTest {

    @Test
    void clearsYellowCardsWhenRedCardIsRecorded() {
        PlayerFixtureStat stat = PlayerFixtureStat.builder().build();

        stat.updateLiveStat(
                90, 7.0, false, false,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                2, 1, 0,
                0, 0, 0, 0, 0
        );

        assertThat(stat.getYellowCards()).isZero();
        assertThat(stat.getRedCards()).isEqualTo(1);
    }

    @Test
    void updateLiveStatStoresZeroForMissingGoalsAndAssistsWhenPlayerAppeared() {
        PlayerFixtureStat stat = PlayerFixtureStat.builder().build();

        updateScoringStats(stat, 90, null, null);

        assertThat(stat.getMinutesPlayed()).isEqualTo(90);
        assertThat(stat.getGoals()).isZero();
        assertThat(stat.getAssists()).isZero();
    }

    @Test
    void updateLiveStatStoresNullForMinutesGoalsAndAssistsWhenPlayerDidNotAppear() {
        PlayerFixtureStat stat = PlayerFixtureStat.builder().build();

        updateScoringStats(stat, 0, 0, 0);

        assertThat(stat.getMinutesPlayed()).isNull();
        assertThat(stat.getGoals()).isNull();
        assertThat(stat.getAssists()).isNull();
    }

    @Test
    void updateLiveStatPreservesRecordedGoalsAndAssistsWhenPlayerAppeared() {
        PlayerFixtureStat stat = PlayerFixtureStat.builder().build();

        updateScoringStats(stat, 75, 1, 2);

        assertThat(stat.getGoals()).isEqualTo(1);
        assertThat(stat.getAssists()).isEqualTo(2);
    }

    private void updateScoringStats(
            PlayerFixtureStat stat,
            Integer minutesPlayed,
            Integer goals,
            Integer assists
    ) {
        stat.updateLiveStat(
                minutesPlayed,
                null,
                false,
                false,
                goals,
                assists,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
