package com.culinary.backend.ingredient.controller;

import com.culinary.backend.ingredient.dto.DietaryTagResponse;
import com.culinary.backend.ingredient.dto.IngredientCategoryResponse;
import com.culinary.backend.ingredient.dto.IngredientResponse;
import com.culinary.backend.ingredient.service.IngredientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @GetMapping("/ingredients")
    public ResponseEntity<List<IngredientResponse>> listIngredients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId
    ) {
        return ResponseEntity.ok(ingredientService.listIngredients(search, categoryId));
    }

    @GetMapping("/ingredient-categories")
    public ResponseEntity<List<IngredientCategoryResponse>> listCategories() {
        return ResponseEntity.ok(ingredientService.listCategories());
    }

    @GetMapping("/dietary-tags")
    public ResponseEntity<List<DietaryTagResponse>> listDietaryTags() {
        return ResponseEntity.ok(ingredientService.listDietaryTags());
    }
}
