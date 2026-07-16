package com.tip.api.dto;

import com.tip.user.UserRole;

public record UpdateUserRequest(
        String displayName,
        UserRole role,
        Boolean tradingEnabled,
        Boolean active
) {
}
