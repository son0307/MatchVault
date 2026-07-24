package com.son.soccerStreaming.admin.dto;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDtoTest {

    @Test
    void normalizesEditableTextFields() {
        AdminDto.TeamUpdateRequest team = new AdminDto.TeamUpdateRequest();
        ReflectionTestUtils.setField(team, "name", "   ");
        ReflectionTestUtils.setField(team, "code", " ARS ");
        team.normalizeTextFields();
        assertThat(team.getName()).isNull();
        assertThat(team.getCode()).isEqualTo("ARS");

        AdminDto.PlayerUpdateRequest player = new AdminDto.PlayerUpdateRequest();
        ReflectionTestUtils.setField(player, "koreanName", "\t");
        ReflectionTestUtils.setField(player, "position", " Midfielder ");
        player.normalizeTextFields();
        assertThat(player.getKoreanName()).isNull();
        assertThat(player.getPosition()).isEqualTo("Midfielder");

        AdminDto.FixtureUpdateRequest fixture = new AdminDto.FixtureUpdateRequest();
        ReflectionTestUtils.setField(fixture, "venueNameKo", "\n");
        ReflectionTestUtils.setField(fixture, "homeFormation", " 4-3-3 ");
        fixture.normalizeTextFields();
        assertThat(fixture.getVenueNameKo()).isNull();
        assertThat(fixture.getHomeFormation()).isEqualTo("4-3-3");

        AdminDto.FixtureEventUpdateRequest event = new AdminDto.FixtureEventUpdateRequest();
        ReflectionTestUtils.setField(event, "comments", "  ");
        ReflectionTestUtils.setField(event, "eventType", " Goal ");
        event.normalizeTextFields();
        assertThat(event.getComments()).isNull();
        assertThat(event.getEventType()).isEqualTo("Goal");

        AdminDto.FixtureLineupUpdateRequest lineup = new AdminDto.FixtureLineupUpdateRequest();
        ReflectionTestUtils.setField(lineup, "grid", "   ");
        ReflectionTestUtils.setField(lineup, "position", " G ");
        lineup.normalizeTextFields();
        assertThat(lineup.getGrid()).isNull();
        assertThat(lineup.getPosition()).isEqualTo("G");
    }
}
