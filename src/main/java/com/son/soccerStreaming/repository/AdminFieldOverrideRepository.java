package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.AdminFieldOverride;
import com.son.soccerStreaming.entity.AdminOverrideTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminFieldOverrideRepository extends JpaRepository<AdminFieldOverride, Long> {

    Optional<AdminFieldOverride> findByTargetTypeAndTargetIdAndFieldName(
            AdminOverrideTargetType targetType,
            Long targetId,
            String fieldName
    );

    List<AdminFieldOverride> findAllByTargetTypeAndTargetIdAndFieldNameIn(
            AdminOverrideTargetType targetType,
            Long targetId,
            Collection<String> fieldNames
    );
}
