package com.culinary.backend.chef.controller;

import com.culinary.backend.chef.dto.ChefDashboardResponse;
import com.culinary.backend.chef.dto.CreateRecipeRequest;
import com.culinary.backend.chef.dto.RecipeSummaryResponse;
import com.culinary.backend.chef.service.ChefService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chef")
public class ChefController {

    private final ChefService chefService;

    public ChefController(ChefService chefService) {
        this.chefService = chefService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ChefDashboardResponse> getDashboard(@RequestParam long userId) {
        return ResponseEntity.ok(chefService.getDashboard(userId));
    }

    @GetMapping("/recipes")
    public ResponseEntity<List<RecipeSummaryResponse>> getRecipes(@RequestParam long userId) {
        return ResponseEntity.ok(chefService.getChefRecipes(userId));
    }

    @PostMapping("/recipes")
    public ResponseEntity<RecipeSummaryResponse> createRecipe(
            @RequestParam long userId,
            @RequestBody CreateRecipeRequest request
    ) {
        return ResponseEntity.ok(chefService.createRecipe(userId, request));
    }

    @DeleteMapping("/recipes/{recipeId}")
    public ResponseEntity<Void> deleteRecipe(
            @PathVariable long recipeId,
            @RequestParam long userId
    ) {
        chefService.deleteRecipe(userId, recipeId);
        return ResponseEntity.noContent().build();
    }
}
