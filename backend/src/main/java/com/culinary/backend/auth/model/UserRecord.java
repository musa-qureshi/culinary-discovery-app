package com.culinary.backend.auth.model;

public record UserRecord(
        long userId,
        String fullName,
        String email,
        String passwordHash,
        UserRole role,
        AccountStatus accountStatus
) {
}
