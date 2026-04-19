package com.culinary.backend.chef.repository;

import com.culinary.backend.chef.dto.RecipeAnalyticsEntry;
import com.culinary.backend.chef.dto.RecipeSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ChefRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChefRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecipeAnalyticsEntry> getChefRecipeAnalytics(long chefId) {
        return jdbcTemplate.query(
                """
                select r.recipe_id, r.title, r.difficulty, r.cook_time_min,
                       coalesce(cl.cooked_count, 0)   as cooked_count,
                       coalesce(rv.avg_rating, 0)      as avg_rating,
                       coalesce(rv.review_count, 0)    as review_count,
                       (coalesce(cl.cooked_count, 0) * 0.50)
                           + (coalesce(rv.avg_rating, 0) * 2.00) as royalty
                from recipe r
                left join (
                    select recipe_id, count(*) as cooked_count
                    from cook_log
                    group by recipe_id
                ) cl on cl.recipe_id = r.recipe_id
                left join (
                    select recipe_id, avg(rating_value) as avg_rating, count(*) as review_count
                    from review
                    group by recipe_id
                ) rv on rv.recipe_id = r.recipe_id
                where r.author_id = ?
                order by royalty desc, r.published_at desc
                """,
                (rs, rowNum) -> new RecipeAnalyticsEntry(
                        rs.getLong("recipe_id"),
                        rs.getString("title"),
                        rs.getString("difficulty"),
                        rs.getInt("cook_time_min"),
                        rs.getLong("cooked_count"),
                        rs.getDouble("avg_rating"),
                        rs.getLong("review_count"),
                        rs.getDouble("royalty")
                ),
                chefId
        );
    }

    public long countChefRecipes(long chefId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from recipe where author_id = ?", Long.class, chefId);
        return count == null ? 0L : count;
    }

    public long createRecipe(long authorId, String title, String description,
                             int baseServings, int cookTimeMin, String difficulty) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    insert into recipe (title, description, base_servings, cook_time_min, difficulty, author_id)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setInt(3, baseServings);
            ps.setInt(4, cookTimeMin);
            ps.setString(5, difficulty);
            ps.setLong(6, authorId);
            return ps;
        }, keyHolder);

        return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    public void addRecipeTag(long recipeId, long tagId) {
        jdbcTemplate.update(
                "insert ignore into recipe_tag (recipe_id, tag_id) values (?, ?)",
                recipeId, tagId
        );
    }

    public void addRecipeIngredient(long recipeId, long ingredientId, double quantity, String unit) {
        jdbcTemplate.update(
                """
                insert into recipe_ingredient (recipe_id, ingredient_id, quantity, unit)
                values (?, ?, ?, ?)
                """,
                recipeId, ingredientId, quantity, unit
        );
    }

    public void addRecipeStep(long recipeId, int stepNo, String instructionText) {
        jdbcTemplate.update(
                """
                insert into recipe_step (recipe_id, step_no, instruction_text)
                values (?, ?, ?)
                """,
                recipeId, stepNo, instructionText
        );
    }

    public void addRecipeMedia(long recipeId, int mediaNo, String mediaType, String url) {
        jdbcTemplate.update(
                """
                insert into recipe_media (recipe_id, media_no, media_type, url)
                values (?, ?, ?, ?)
                """,
                recipeId, mediaNo, mediaType, url
        );
    }

    public List<RecipeSummaryResponse> getChefRecipes(long chefId) {
        List<RecipeSummaryResponse> recipes = jdbcTemplate.query(
                """
                select r.recipe_id, r.title, r.description, r.base_servings,
                       r.cook_time_min, r.difficulty, r.published_at
                from recipe r
                where r.author_id = ?
                order by r.published_at desc
                """,
                (rs, rowNum) -> new RecipeSummaryResponse(
                        rs.getLong("recipe_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("base_servings"),
                        rs.getInt("cook_time_min"),
                        rs.getString("difficulty"),
                        toLocalDateTime(rs.getObject("published_at")),
                        List.of()
                ),
                chefId
        );

        return recipes.stream().map(r -> new RecipeSummaryResponse(
                r.recipeId(),
                r.title(),
                r.description(),
                r.baseServings(),
                r.cookTimeMin(),
                r.difficulty(),
                r.publishedAt(),
                getRecipeTags(r.recipeId())
        )).toList();
    }

    public List<String> getRecipeTags(long recipeId) {
        return jdbcTemplate.query(
                """
                select dt.name
                from recipe_tag rt
                join dietary_tag dt on dt.tag_id = rt.tag_id
                where rt.recipe_id = ?
                order by dt.name asc
                """,
                (rs, rowNum) -> rs.getString("name"),
                recipeId
        );
    }

    public String getChefBio(long chefId) {
        List<String> rows = jdbcTemplate.query(
                "select bio from verified_chef_profile where user_id = ?",
                (rs, rowNum) -> rs.getString("bio"),
                chefId
        );
        return rows.stream().findFirst().orElse(null);
    }

    public int deleteRecipe(long recipeId, long authorId) {
        return jdbcTemplate.update(
                "delete from recipe where recipe_id = ? and author_id = ?",
                recipeId, authorId
        );
    }

    private LocalDateTime toLocalDateTime(Object rawValue) {
        if (rawValue instanceof LocalDateTime value) {
            return value;
        }
        if (rawValue instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
