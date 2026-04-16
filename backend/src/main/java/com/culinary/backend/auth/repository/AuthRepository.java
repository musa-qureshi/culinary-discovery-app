package com.culinary.backend.auth.repository;

import com.culinary.backend.auth.model.AccountStatus;
import com.culinary.backend.auth.model.UserRecord;
import com.culinary.backend.auth.model.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<UserRecord> USER_ROW_MAPPER = (rs, rowNum) -> new UserRecord(
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            UserRole.valueOf(rs.getString("role")),
            AccountStatus.valueOf(rs.getString("account_status"))
    );

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserRecord> findUserByEmail(String email) {
        List<UserRecord> rows = jdbcTemplate.query(
                """
                select user_id, full_name, email, password_hash, role, account_status
                from app_user
                where email = ?
                """,
                USER_ROW_MAPPER,
                email
        );

        return rows.stream().findFirst();
    }

    public Optional<UserRecord> findUserById(long userId) {
        List<UserRecord> rows = jdbcTemplate.query(
                """
                select user_id, full_name, email, password_hash, role, account_status
                from app_user
                where user_id = ?
                """,
                USER_ROW_MAPPER,
                userId
        );

        return rows.stream().findFirst();
    }

    public long createUser(String fullName, String email, String passwordHash, UserRole role, AccountStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    insert into app_user (full_name, email, password_hash, role, account_status)
                    values (?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, role.name());
            ps.setString(5, status.name());
            return ps;
        }, keyHolder);

        return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    public void createVerifiedChefProfile(long userId, String bio) {
        jdbcTemplate.update(
                """
                insert into verified_chef_profile (user_id, bio, verification_status)
                values (?, ?, 'PENDING')
                """,
                userId,
                bio
        );
    }

    public void approveVerifiedChef(long userId, long adminUserId, String reviewNote) {
        jdbcTemplate.update(
                """
                update verified_chef_profile
                set verification_status = 'APPROVED',
                    reviewed_at = current_timestamp,
                    reviewed_by = ?,
                    review_note = ?
                where user_id = ?
                """,
                adminUserId,
                reviewNote,
                userId
        );

        jdbcTemplate.update(
                """
                update app_user
                set account_status = 'ACTIVE'
                where user_id = ? and role = 'VERIFIED_CHEF'
                """,
                userId
        );
    }
}
