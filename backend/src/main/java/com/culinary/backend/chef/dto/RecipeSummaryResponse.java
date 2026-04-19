package com.culinary.backend.chef.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecipeSummaryResponse(
        long recipeId,
        String title,
        String description,
        int baseServings,
        int cookTimeMin,
        String difficulty,
        LocalDateTime publishedAt,
        List<String> tags
) {}
