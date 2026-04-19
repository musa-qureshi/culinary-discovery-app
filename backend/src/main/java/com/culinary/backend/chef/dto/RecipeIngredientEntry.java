package com.culinary.backend.chef.dto;

public record RecipeIngredientEntry(
        long ingredientId,
        double quantity,
        String unit
) {}
