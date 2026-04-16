package com.culinary.backend.auth.dto;

import java.time.LocalDateTime;

public record PendingChefResponse(
        long userId,
        String fullName,
        String email,
        String bio,
        LocalDateTime requestedAt
) {
}