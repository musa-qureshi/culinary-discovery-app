package com.culinary.backend.auth.dto;

public record AuthResponse(
        long userId,
        String fullName,
        String email,
        String role,
        String accountStatus,
        String message
) {
}
