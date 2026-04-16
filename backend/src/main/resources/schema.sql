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
