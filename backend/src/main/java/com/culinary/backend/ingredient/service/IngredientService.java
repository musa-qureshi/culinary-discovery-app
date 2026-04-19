package com.culinary.backend.ingredient.service;

import com.culinary.backend.ingredient.dto.DietaryTagResponse;
import com.culinary.backend.ingredient.dto.IngredientCategoryResponse;
import com.culinary.backend.ingredient.dto.IngredientResponse;
import com.culinary.backend.ingredient.repository.IngredientRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    public IngredientService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    public List<IngredientResponse> listIngredients(String search, Long categoryId) {
        return ingredientRepository.listIngredients(search, categoryId);
    }

    public List<IngredientCategoryResponse> listCategories() {
        return ingredientRepository.listCategories();
    }

    public List<DietaryTagResponse> listDietaryTags() {
        return ingredientRepository.listDietaryTags();
    }
}
