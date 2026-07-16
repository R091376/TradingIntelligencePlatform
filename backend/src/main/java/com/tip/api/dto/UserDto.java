package com.tip.api.dto;

import com.tip.user.AppUserEntity;
import com.tip.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String displayName,
        /** Wire as plain string ADMIN|USER (avoids enum serialization surprises). */
        String role,
        BigDecimal cashBalance,
        boolean tradingEnabled,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserDto from(AppUserEntity e) {
        UserRole r = e.getRole();
        return new UserDto(
                e.getId(),
                e.getUsername(),
                e.getDisplayName(),
                r != null ? r.name() : UserRole.USER.name(),
                e.getCashBalance(),
                e.isTradingEnabled(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
