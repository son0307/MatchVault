package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOverrideServiceTest {

    @Mock
    private AdminFieldOverrideRepository adminFieldOverrideRepository;

    @InjectMocks
    private AdminOverrideService adminOverrideService;

    @Test
    void overriddenFieldsReturnsOnlyStoredFieldNames() {
        AdminFieldOverride nameOverride = AdminFieldOverride.builder()
                .targetType(AdminOverrideTargetType.TEAM)
                .targetId(47L)
                .fieldName("name")
                .updatedAt(LocalDateTime.now())
                .build();

        when(adminFieldOverrideRepository.findAllByTargetTypeAndTargetIdAndFieldNameIn(
                AdminOverrideTargetType.TEAM,
                47L,
                List.of("name", "logoUrl")
        )).thenReturn(List.of(nameOverride));

        Set<String> fields = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.TEAM,
                47L,
                List.of("name", "logoUrl")
        );

        assertThat(fields).containsExactly("name");
    }

    @Test
    void overrideInfosIncludesUpdatedAt() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 22, 10, 30);
        AdminFieldOverride override = AdminFieldOverride.builder()
                .targetType(AdminOverrideTargetType.PLAYER)
                .targetId(10L)
                .fieldName("photoUrl")
                .updatedAt(updatedAt)
                .build();

        when(adminFieldOverrideRepository.findAllByTargetTypeAndTargetIdOrderByFieldNameAsc(
                AdminOverrideTargetType.PLAYER,
                10L
        )).thenReturn(List.of(override));

        List<AdminOverrideService.OverrideInfo> infos = adminOverrideService.overrideInfos(
                AdminOverrideTargetType.PLAYER,
                10L
        );

        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).fieldName()).isEqualTo("photoUrl");
        assertThat(infos.get(0).updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void clearMissingOverrideReturnsZeroWithoutFailure() {
        when(adminFieldOverrideRepository.deleteByTargetTypeAndTargetIdAndFieldName(
                AdminOverrideTargetType.TEAM,
                47L,
                "name"
        )).thenReturn(0L);

        long deletedCount = adminOverrideService.clearOverride(AdminOverrideTargetType.TEAM, 47L, "name");

        assertThat(deletedCount).isZero();
        verify(adminFieldOverrideRepository).deleteByTargetTypeAndTargetIdAndFieldName(
                AdminOverrideTargetType.TEAM,
                47L,
                "name"
        );
    }
}
