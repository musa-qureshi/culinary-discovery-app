package com.culinary.backend.chef.service;

import com.culinary.backend.auth.model.AccountStatus;
import com.culinary.backend.auth.model.UserRecord;
import com.culinary.backend.auth.model.UserRole;
import com.culinary.backend.auth.repository.AuthRepository;
import com.culinary.backend.chef.dto.*;
import com.culinary.backend.chef.repository.ChefRepository;
import com.culinary.backend.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChefService {

    private final ChefRepository chefRepository;
    private final AuthRepository authRepository;

    public ChefService(ChefRepository chefRepository, AuthRepository authRepository) {
        this.chefRepository = chefRepository;
        this.authRepository = authRepository;
    }

    public ChefDashboardResponse getDashboard(long chefId) {
        UserRecord chef = requireActiveChef(chefId);

        List<RecipeAnalyticsEntry> analytics = chefRepository.getChefRecipeAnalytics(chefId);

        double totalRoyalty = analytics.stream().mapToDouble(RecipeAnalyticsEntry::royalty).sum();
        long totalCooks = analytics.stream().mapToLong(RecipeAnalyticsEntry::cookedCount).sum();
        long totalReviews = analytics.stream().mapToLong(RecipeAnalyticsEntry::reviewCount).sum();
        double overallAvgRating = totalReviews == 0 ? 0.0
                : analytics.stream()
                        .filter(e -> e.reviewCount() > 0)
                        .mapToDouble(e -> e.avgRating() * e.reviewCount())
                        .sum() / totalReviews;

        String bio = chefRepository.getChefBio(chefId);

        return new ChefDashboardResponse(
                chef.userId(),
                chef.fullName(),
                chef.email(),
                bio,
                totalRoyalty,
                analytics.size(),
                totalCooks,
                overallAvgRating,
                analytics
        );
    }

    public List<RecipeSummaryResponse> getChefRecipes(long chefId) {
        requireActiveChef(chefId);
        return chefRepository.getChefRecipes(chefId);
    }

    @Transactional
    public RecipeSummaryResponse createRecipe(long chefId, CreateRecipeRequest request) {
        requireActiveChef(chefId);

        if (request.title() == null || request.title().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recipe title is required.");
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one step is required.");
        }
        if (request.ingredients() == null || request.ingredients().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one ingredient is required.");
        }

        String difficulty = request.difficulty() == null ? "Easy" : request.difficulty().trim();
        if (!difficulty.equals("Easy") && !difficulty.equals("Medium") && !difficulty.equals("Hard")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Difficulty must be Easy, Medium, or Hard.");
        }

        long recipeId = chefRepository.createRecipe(
                chefId,
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                request.baseServings() <= 0 ? 2 : request.baseServings(),
                request.cookTimeMin() <= 0 ? 30 : request.cookTimeMin(),
                difficulty
        );

        if (recipeId == 0L) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create recipe.");
        }

        if (request.tagIds() != null) {
            for (Long tagId : request.tagIds()) {
                chefRepository.addRecipeTag(recipeId, tagId);
            }
        }

        for (RecipeIngredientEntry ing : request.ingredients()) {
            chefRepository.addRecipeIngredient(recipeId, ing.ingredientId(), ing.quantity(), ing.unit());
        }

        for (RecipeStepEntry step : request.steps()) {
            chefRepository.addRecipeStep(recipeId, step.stepNo(), step.instructionText());
        }

        if (request.media() != null) {
            for (RecipeMediaEntry m : request.media()) {
                chefRepository.addRecipeMedia(recipeId, m.mediaNo(), m.mediaType(), m.url());
            }
        }

        List<String> tags = chefRepository.getRecipeTags(recipeId);

        return new RecipeSummaryResponse(
                recipeId,
                request.title().trim(),
                request.description(),
                request.baseServings() <= 0 ? 2 : request.baseServings(),
                request.cookTimeMin() <= 0 ? 30 : request.cookTimeMin(),
                difficulty,
                null,
                tags
        );
    }

    @Transactional
    public void deleteRecipe(long chefId, long recipeId) {
        requireActiveChef(chefId);
        int deleted = chefRepository.deleteRecipe(recipeId, chefId);
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Recipe not found or does not belong to you.");
        }
    }

    private UserRecord requireActiveChef(long chefId) {
        UserRecord chef = authRepository.findUserById(chefId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef user not found."));

        if (chef.role() != UserRole.VERIFIED_CHEF) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only VERIFIED_CHEF users can access this resource.");
        }

        if (chef.accountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your chef account is not yet active.");
        }

        return chef;
    }
}
