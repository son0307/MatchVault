package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureDetailGenerationStoreTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private FixtureDetailGenerationStore store;

    @Test
    void repeatedDeliveryDoesNotPublishAgain() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(2025)))
                .thenReturn(List.of(7L));

        store.publish(2025, 7L);

        verify(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq(2025));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void olderDeliveryCannotReplaceNewerGeneration() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(2025)))
                .thenReturn(List.of(8L));

        assertThatThrownBy(() -> store.publish(2025, 7L))
                .isInstanceOf(IllegalStateException.class);

        verify(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq(2025));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void fixtureDraftUpdateBindsEveryColumnIncludingLineupTactics() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(7L)))
                .thenReturn("BUILDING");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("home_formation = ?", "away_goalkeeper_color_border = ?");
            assertThat(sql.chars().filter(character -> character == '?').count())
                    .isEqualTo(invocation.getArguments().length - 1);
            return 1;
        });

        store.applyFixtureResponse(7L, Fixture.builder().fixtureId(100L).season(2025).build());
    }
}
