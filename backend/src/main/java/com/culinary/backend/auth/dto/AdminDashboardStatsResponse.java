package com.culinary.backend.auth.dto;

public record AdminDashboardStatsResponse(
        long totalUsers,
        long homeCookCount,
        long supplierCount,
        long verifiedChefCount,
        long adminCount,
        long pendingChefVerifications,
        long activeUsers,
        long blockedUsers
) {
}