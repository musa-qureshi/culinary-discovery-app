package com.culinary.backend.chef.dto;

import java.util.List;

public record ChefDashboardResponse(
        long chefId,
        String fullName,
        String email,
        String bio,
        double totalRoyalty,
        long totalRecipes,
        long totalCooks,
        double overallAvgRating,
        List<RecipeAnalyticsEntry> recipeAnalytics
) {}
