create table if not exists app_user (
    user_id bigint unsigned not null auto_increment,
    full_name varchar(100) not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    role enum('HOME_COOK', 'VERIFIED_CHEF', 'SUPPLIER', 'ADMIN') not null,
    account_status enum('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED') not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (user_id),
    unique key uq_app_user_email (email)
);

create table if not exists verified_chef_profile (
    user_id bigint unsigned not null,
    bio varchar(255) null,
    verification_status enum('PENDING', 'APPROVED', 'REJECTED') not null default 'PENDING',
    royalty decimal(12, 2) not null default 0,
    requested_at timestamp not null default current_timestamp,
    reviewed_at timestamp null,
    reviewed_by bigint unsigned null,
    review_note varchar(255) null,
    primary key (user_id),
    constraint fk_verified_chef_profile_user
        foreign key (user_id) references app_user(user_id)
        on delete cascade,
    constraint fk_verified_chef_profile_admin
        foreign key (reviewed_by) references app_user(user_id)
        on delete set null
);

create table if not exists ingredient_category (
    category_id bigint unsigned not null auto_increment,
    name varchar(50) not null,
    primary key (category_id),
    unique key uq_ingredient_category_name (name)
);

create table if not exists ingredient (
    ingredient_id bigint unsigned not null auto_increment,
    name varchar(50) not null,
    calories int not null default 0,
    carbs decimal(6, 2) not null default 0,
    protein decimal(6, 2) not null default 0,
    fat decimal(6, 2) not null default 0,
    category_id bigint unsigned null,
    primary key (ingredient_id),
    unique key uq_ingredient_name (name),
    constraint fk_ingredient_category
        foreign key (category_id) references ingredient_category(category_id)
        on delete set null
);

create table if not exists dietary_tag (
    tag_id bigint unsigned not null auto_increment,
    name varchar(50) not null,
    primary key (tag_id),
    unique key uq_dietary_tag_name (name)
);

create table if not exists recipe (
    recipe_id bigint unsigned not null auto_increment,
    title varchar(100) not null,
    description varchar(500) null,
    base_servings int not null default 2,
    cook_time_min int not null default 30,
    difficulty enum('Easy', 'Medium', 'Hard') not null default 'Easy',
    published_at timestamp not null default current_timestamp,
    author_id bigint unsigned not null,
    primary key (recipe_id),
    constraint fk_recipe_author
        foreign key (author_id) references app_user(user_id)
        on delete cascade
);

create table if not exists recipe_tag (
    recipe_id bigint unsigned not null,
    tag_id bigint unsigned not null,
    primary key (recipe_id, tag_id),
    constraint fk_recipe_tag_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade,
    constraint fk_recipe_tag_tag
        foreign key (tag_id) references dietary_tag(tag_id)
        on delete cascade
);

create table if not exists recipe_ingredient (
    recipe_id bigint unsigned not null,
    ingredient_id bigint unsigned not null,
    quantity decimal(10, 3) not null,
    unit varchar(20) not null,
    primary key (recipe_id, ingredient_id),
    constraint fk_recipe_ingredient_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade,
    constraint fk_recipe_ingredient_ingredient
        foreign key (ingredient_id) references ingredient(ingredient_id)
        on delete cascade
);

create table if not exists recipe_step (
    recipe_id bigint unsigned not null,
    step_no int not null,
    instruction_text varchar(500) not null,
    primary key (recipe_id, step_no),
    constraint fk_recipe_step_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade
);

create table if not exists recipe_media (
    recipe_id bigint unsigned not null,
    media_no int not null,
    media_type varchar(20) not null,
    url varchar(2048) not null,
    primary key (recipe_id, media_no),
    constraint fk_recipe_media_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade
);

create table if not exists review (
    review_id bigint unsigned not null auto_increment,
    rating_value int not null,
    review_text varchar(500) null,
    created_at timestamp not null default current_timestamp,
    recipe_id bigint unsigned not null,
    given_by bigint unsigned not null,
    primary key (review_id),
    constraint fk_review_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade,
    constraint fk_review_user
        foreign key (given_by) references app_user(user_id)
        on delete cascade,
    constraint chk_review_rating
        check (rating_value between 1 and 5)
);

create table if not exists cook_log (
    cooklog_id bigint unsigned not null auto_increment,
    rating_value int null,
    servings int not null default 1,
    logged_at timestamp not null default current_timestamp,
    given_by bigint unsigned not null,
    recipe_id bigint unsigned not null,
    primary key (cooklog_id),
    constraint fk_cook_log_user
        foreign key (given_by) references app_user(user_id)
        on delete cascade,
    constraint fk_cook_log_recipe
        foreign key (recipe_id) references recipe(recipe_id)
        on delete cascade
);

-- Seed ingredient categories (insert ignore to be idempotent)
insert ignore into ingredient_category (name) values
('Vegetables'),
('Fruits'),
('Proteins'),
('Grains & Pasta'),
('Dairy'),
('Spices & Herbs'),
('Oils & Fats'),
('Condiments & Sauces');

-- Seed ingredients
insert ignore into ingredient (name, calories, carbs, protein, fat, category_id) values
('Tomato', 18, 3.9, 0.9, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Roma Tomato', 18, 3.9, 0.9, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Vine Tomato', 18, 3.9, 0.9, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Onion', 40, 9.3, 1.1, 0.1, (select category_id from ingredient_category where name = 'Vegetables')),
('Garlic', 149, 33.1, 6.4, 0.5, (select category_id from ingredient_category where name = 'Vegetables')),
('Potato', 77, 17.5, 2.0, 0.1, (select category_id from ingredient_category where name = 'Vegetables')),
('Carrot', 41, 9.6, 0.9, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Spinach', 23, 3.6, 2.9, 0.4, (select category_id from ingredient_category where name = 'Vegetables')),
('Bell Pepper', 31, 6.0, 1.0, 0.3, (select category_id from ingredient_category where name = 'Vegetables')),
('Cucumber', 15, 3.6, 0.7, 0.1, (select category_id from ingredient_category where name = 'Vegetables')),
('Zucchini', 17, 3.1, 1.2, 0.3, (select category_id from ingredient_category where name = 'Vegetables')),
('Broccoli', 34, 6.6, 2.8, 0.4, (select category_id from ingredient_category where name = 'Vegetables')),
('Mushroom', 22, 3.3, 3.1, 0.3, (select category_id from ingredient_category where name = 'Vegetables')),
('Eggplant', 25, 5.9, 1.0, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Celery', 16, 3.0, 0.7, 0.2, (select category_id from ingredient_category where name = 'Vegetables')),
('Leek', 61, 14.2, 1.5, 0.3, (select category_id from ingredient_category where name = 'Vegetables')),
('Lemon', 29, 9.3, 1.1, 0.3, (select category_id from ingredient_category where name = 'Fruits')),
('Lime', 30, 10.5, 0.7, 0.2, (select category_id from ingredient_category where name = 'Fruits')),
('Apple', 52, 13.8, 0.3, 0.2, (select category_id from ingredient_category where name = 'Fruits')),
('Banana', 89, 22.8, 1.1, 0.3, (select category_id from ingredient_category where name = 'Fruits')),
('Orange', 47, 11.8, 0.9, 0.1, (select category_id from ingredient_category where name = 'Fruits')),
('Chicken Breast', 165, 0, 31.0, 3.6, (select category_id from ingredient_category where name = 'Proteins')),
('Ground Beef', 254, 0, 17.2, 20.0, (select category_id from ingredient_category where name = 'Proteins')),
('Salmon Fillet', 208, 0, 20.4, 13.4, (select category_id from ingredient_category where name = 'Proteins')),
('Eggs', 155, 1.1, 13.0, 11.0, (select category_id from ingredient_category where name = 'Proteins')),
('Tofu', 76, 1.9, 8.1, 4.8, (select category_id from ingredient_category where name = 'Proteins')),
('Chickpeas', 164, 27.4, 8.9, 2.6, (select category_id from ingredient_category where name = 'Proteins')),
('Shrimp', 99, 0.2, 24.0, 0.3, (select category_id from ingredient_category where name = 'Proteins')),
('Lamb Chops', 294, 0, 24.5, 21.0, (select category_id from ingredient_category where name = 'Proteins')),
('All-Purpose Flour', 364, 76.3, 10.3, 1.0, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('White Rice', 130, 28.2, 2.7, 0.3, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('Pasta', 371, 74.7, 13.0, 1.1, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('Bread Crumbs', 395, 73.0, 13.0, 5.3, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('Oats', 389, 66.3, 16.9, 6.9, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('Quinoa', 368, 64.2, 14.1, 6.1, (select category_id from ingredient_category where name = 'Grains & Pasta')),
('Butter', 717, 0.1, 0.9, 81.1, (select category_id from ingredient_category where name = 'Dairy')),
('Whole Milk', 61, 4.8, 3.2, 3.3, (select category_id from ingredient_category where name = 'Dairy')),
('Heavy Cream', 340, 2.8, 2.8, 36.1, (select category_id from ingredient_category where name = 'Dairy')),
('Cheddar Cheese', 403, 1.3, 25.0, 33.1, (select category_id from ingredient_category where name = 'Dairy')),
('Parmesan Cheese', 431, 4.1, 38.5, 28.6, (select category_id from ingredient_category where name = 'Dairy')),
('Greek Yogurt', 59, 3.6, 10.2, 0.4, (select category_id from ingredient_category where name = 'Dairy')),
('Mozzarella', 280, 2.2, 28.0, 17.0, (select category_id from ingredient_category where name = 'Dairy')),
('Salt', 0, 0, 0, 0, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Black Pepper', 251, 63.7, 10.4, 3.3, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Cumin', 375, 44.2, 17.8, 22.3, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Paprika', 282, 53.9, 14.1, 12.9, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Oregano', 265, 68.9, 9.0, 4.3, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Basil', 23, 2.7, 3.2, 0.6, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Thyme', 101, 24.5, 5.6, 1.7, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Cinnamon', 247, 80.6, 4.0, 1.2, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Rosemary', 131, 20.7, 3.3, 5.9, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Turmeric', 354, 67.1, 7.8, 9.9, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Chili Flakes', 282, 49.7, 12.0, 14.3, (select category_id from ingredient_category where name = 'Spices & Herbs')),
('Olive Oil', 884, 0, 0, 100.0, (select category_id from ingredient_category where name = 'Oils & Fats')),
('Vegetable Oil', 884, 0, 0, 100.0, (select category_id from ingredient_category where name = 'Oils & Fats')),
('Coconut Oil', 862, 0, 0, 100.0, (select category_id from ingredient_category where name = 'Oils & Fats')),
('Sesame Oil', 884, 0, 0, 100.0, (select category_id from ingredient_category where name = 'Oils & Fats')),
('Soy Sauce', 53, 5.6, 8.1, 0.1, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('Tomato Paste', 82, 18.9, 4.3, 0.4, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('Honey', 304, 82.4, 0.3, 0, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('White Vinegar', 21, 0.9, 0, 0, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('Balsamic Vinegar', 88, 17.0, 0.5, 0, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('Dijon Mustard', 66, 8.2, 3.7, 3.3, (select category_id from ingredient_category where name = 'Condiments & Sauces')),
('Worcestershire Sauce', 78, 19.5, 2.7, 0, (select category_id from ingredient_category where name = 'Condiments & Sauces'));

-- Seed dietary tags
insert ignore into dietary_tag (name) values
('Vegan'),
('Vegetarian'),
('Keto'),
('Gluten-Free'),
('Dairy-Free'),
('High-Protein'),
('Low-Carb'),
('Nut-Free'),
('Paleo');
