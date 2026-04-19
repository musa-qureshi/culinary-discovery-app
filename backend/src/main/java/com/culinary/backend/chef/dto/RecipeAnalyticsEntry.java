package com.culinary.backend.chef.dto;

public record RecipeAnalyticsEntry(
        long recipeId,
        String title,
        String difficulty,
        int cookTimeMin,
        long cookedCount,
        double avgRating,
        long reviewCount,
        double royalty
) {}
