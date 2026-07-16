package com.tip.api.dto;

import com.tip.user.UserRole;

import java.math.BigDecimal;

public record CreateUserRequest(
        String username,
        String password,
        String displayName,
        UserRole role,
        BigDecimal seedCash
) {
}
