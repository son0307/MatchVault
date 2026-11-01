package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationStore;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

/** Serializes administrator edits with a season publication and checks the viewed generation. */
@Component
@RequiredArgsConstructor
public class AdminFixtureGenerationGuard {

    public static final String HEADER = "X-Fixture-Generation";

    private final FixtureDetailGenerationResolver resolver;
    private final FixtureDetailGenerationStore generationStore;
    private final JdbcTemplate jdbcTemplate;

    public void verifyMutation(Integer season) {
        if (!resolver.enabled() || season == null) {
            return;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new CustomException(ErrorCode.ADMIN_EDIT_CONFLICT);
        }
        HttpServletRequest request = attributes.getRequest();
        if ("GET".equals(request.getMethod())) {
            return;
        }
        String supplied = request.getHeader(HEADER);
        Long expected = null;
        try {
            if (supplied != null && !supplied.isBlank()) {
                expected = Long.valueOf(supplied);
            }
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.ADMIN_EDIT_CONFLICT);
        }
        List<Integer> locks = jdbcTemplate.queryForList(
                "SELECT season FROM fixture_detail_season_lock WHERE season = ? FOR UPDATE",
                Integer.class, season);
        if (locks.size() != 1 || !Objects.equals(expected, generationStore.activeGenerationId(season))) {
            throw new CustomException(ErrorCode.ADMIN_EDIT_CONFLICT);
        }
    }
}
