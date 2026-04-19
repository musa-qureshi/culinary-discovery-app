package com.culinary.backend.ingredient.repository;

import com.culinary.backend.ingredient.dto.DietaryTagResponse;
import com.culinary.backend.ingredient.dto.IngredientCategoryResponse;
import com.culinary.backend.ingredient.dto.IngredientResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class IngredientRepository {

    private final JdbcTemplate jdbcTemplate;

    public IngredientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IngredientResponse> listIngredients(String search, Long categoryId) {
        StringBuilder sql = new StringBuilder(
                """
                select i.ingredient_id, i.name, i.calories, i.carbs, i.protein, i.fat,
                       i.category_id, c.name as category_name
                from ingredient i
                left join ingredient_category c on c.category_id = i.category_id
                where 1 = 1
                """
        );
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append(" and lower(i.name) like ?");
            params.add("%" + search.trim().toLowerCase() + "%");
        }

        if (categoryId != null) {
            sql.append(" and i.category_id = ?");
            params.add(categoryId);
        }

        sql.append(" order by i.name asc");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new IngredientResponse(
                        rs.getLong("ingredient_id"),
                        rs.getString("name"),
                        rs.getInt("calories"),
                        rs.getDouble("carbs"),
                        rs.getDouble("protein"),
                        rs.getDouble("fat"),
                        rs.getObject("category_id") != null ? rs.getLong("category_id") : null,
                        rs.getString("category_name")
                ),
                params.toArray()
        );
    }

    public List<IngredientCategoryResponse> listCategories() {
        return jdbcTemplate.query(
                "select category_id, name from ingredient_category order by name asc",
                (rs, rowNum) -> new IngredientCategoryResponse(
                        rs.getLong("category_id"),
                        rs.getString("name")
                )
        );
    }

    public List<DietaryTagResponse> listDietaryTags() {
        return jdbcTemplate.query(
                "select tag_id, name from dietary_tag order by name asc",
                (rs, rowNum) -> new DietaryTagResponse(
                        rs.getLong("tag_id"),
                        rs.getString("name")
                )
        );
    }
}
