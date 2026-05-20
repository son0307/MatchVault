package com.son.soccerStreaming.service;

import com.son.soccerStreaming.entity.AdminFieldOverride;
import com.son.soccerStreaming.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.repository.AdminFieldOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOverrideService {

    private final AdminFieldOverrideRepository adminFieldOverrideRepository;

    @Transactional
    public void markOverrides(AdminOverrideTargetType targetType, Long targetId, Collection<String> fieldNames) {
        for (String fieldName : fieldNames) {
            adminFieldOverrideRepository
                    .findByTargetTypeAndTargetIdAndFieldName(targetType, targetId, fieldName)
                    .ifPresentOrElse(
                            AdminFieldOverride::touch,
                            () -> adminFieldOverrideRepository.save(AdminFieldOverride.of(targetType, targetId, fieldName))
                    );
        }
    }

    @Transactional(readOnly = true)
    public Set<String> overriddenFields(AdminOverrideTargetType targetType, Long targetId, Collection<String> fieldNames) {
        return adminFieldOverrideRepository
                .findAllByTargetTypeAndTargetIdAndFieldNameIn(targetType, targetId, fieldNames)
                .stream()
                .map(AdminFieldOverride::getFieldName)
                .collect(Collectors.toSet());
    }

    public <T> T apiValueUnlessOverridden(Set<String> overrides, String fieldName, T currentValue, T apiValue) {
        return overrides.contains(fieldName) ? currentValue : apiValue;
    }
}
