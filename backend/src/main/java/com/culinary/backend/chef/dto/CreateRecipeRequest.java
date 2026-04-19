package com.culinary.backend.chef.dto;

import java.util.List;

public record CreateRecipeRequest(
        String title,
        String description,
        int baseServings,
        int cookTimeMin,
        String difficulty,
        List<Long> tagIds,
        List<RecipeIngredientEntry> ingredients,
        List<RecipeStepEntry> steps,
        List<RecipeMediaEntry> media
) {}
