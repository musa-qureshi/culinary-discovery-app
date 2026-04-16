-- Auth foundation schema (Step 1)
-- MySQL 8+

create database if not exists culinary_discovery_app;
use culinary_discovery_app;

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
    unique key uq_app_user_email (email),
    check (email like '%_@_%.__%'),
    check (
        (role = 'VERIFIED_CHEF' and account_status in ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED'))
        or
        (role <> 'VERIFIED_CHEF' and account_status in ('ACTIVE', 'BLOCKED'))
    )
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

delimiter $$

drop trigger if exists trg_verified_chef_profile_insert_guard$$

create trigger trg_verified_chef_profile_insert_guard
before insert on verified_chef_profile
for each row
begin
    declare v_role varchar(20);
    declare v_admin_role varchar(20);

    select role into v_role
    from app_user
    where user_id = new.user_id;

    if v_role <> 'VERIFIED_CHEF' then
        signal sqlstate '45000'
            set message_text = 'Only VERIFIED_CHEF users can have a verified_chef_profile row.';
    end if;

    if new.reviewed_by is not null then
        select role into v_admin_role
        from app_user
        where user_id = new.reviewed_by;

        if v_admin_role <> 'ADMIN' then
            signal sqlstate '45000'
                set message_text = 'reviewed_by must reference a user with ADMIN role.';
        end if;
    end if;

    if new.reviewed_by is null and new.verification_status <> 'PENDING' then
        signal sqlstate '45000'
            set message_text = 'Unreviewed verified chef profiles must stay in PENDING status.';
    end if;
end$$

delimiter ;

-- Registration rule (application-side):
-- role = VERIFIED_CHEF  -> account_status = PENDING_VERIFICATION
-- all other roles       -> account_status = ACTIVE

-- Login rule (application-side):
-- allow login only when account_status = ACTIVE
