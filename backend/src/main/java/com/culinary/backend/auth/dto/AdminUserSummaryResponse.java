package com.culinary.backend.auth.dto;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        long userId,
        String fullName,
        String email,
        String role,
        String accountStatus,
        LocalDateTime createdAt
) {
}