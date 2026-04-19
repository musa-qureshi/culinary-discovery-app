package com.culinary.backend.ingredient.dto;

public record IngredientResponse(
        long ingredientId,
        String name,
        int calories,
        double carbs,
        double protein,
        double fat,
        Long categoryId,
        String categoryName
) {}
