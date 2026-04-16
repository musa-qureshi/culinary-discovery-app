package com.culinary.backend.auth.repository;

import com.culinary.backend.auth.dto.AdminUserSummaryResponse;
import com.culinary.backend.auth.dto.ApprovedChefResponse;
import com.culinary.backend.auth.dto.PendingChefResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public long countTotalUsers() {
        return countByQuery("select count(*) from app_user");
    }

    public long countUsersByRole(UserRole role) {
        return countByQuery("select count(*) from app_user where role = ?", role.name());
    }

    public long countUsersByStatus(AccountStatus status) {
        return countByQuery("select count(*) from app_user where account_status = ?", status.name());
    }

    public long countPendingChefVerifications() {
        return countByQuery("select count(*) from verified_chef_profile where verification_status = 'PENDING'");
    }

    public List<AdminUserSummaryResponse> listUsers(UserRole roleFilter, String searchTerm) {
        StringBuilder sql = new StringBuilder(
                """
                select user_id, full_name, email, role, account_status, created_at
                from app_user
                where 1 = 1
                """
        );
        List<Object> params = new ArrayList<>();

        if (roleFilter != null) {
            sql.append(" and role = ?");
            params.add(roleFilter.name());
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            sql.append(" and (lower(full_name) like ? or lower(email) like ?)");
            String q = "%" + searchTerm.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
        }

        sql.append(" order by created_at desc, user_id desc");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new AdminUserSummaryResponse(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("account_status"),
                        toLocalDateTime(rs.getObject("created_at"))
                ),
                params.toArray()
        );
    }

    public List<PendingChefResponse> listPendingVerifiedChefs() {
        return jdbcTemplate.query(
                """
                select u.user_id, u.full_name, u.email, p.bio, p.requested_at
                from verified_chef_profile p
                join app_user u on u.user_id = p.user_id
                where p.verification_status = 'PENDING'
                order by p.requested_at asc, u.user_id asc
                """,
                (rs, rowNum) -> new PendingChefResponse(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("bio"),
                        toLocalDateTime(rs.getObject("requested_at"))
                )
        );
    }

    public List<ApprovedChefResponse> listApprovedVerifiedChefs() {
        return jdbcTemplate.query(
                """
                select u.user_id, u.full_name, u.email, p.bio, p.requested_at, p.reviewed_at
                from verified_chef_profile p
                join app_user u on u.user_id = p.user_id
                where p.verification_status = 'APPROVED'
                order by p.reviewed_at desc, p.requested_at asc, u.user_id asc
                """,
                (rs, rowNum) -> new ApprovedChefResponse(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("bio"),
                        toLocalDateTime(rs.getObject("requested_at")),
                        toLocalDateTime(rs.getObject("reviewed_at"))
                )
        );
    }

    public int updateUserStatus(long userId, AccountStatus status) {
        return jdbcTemplate.update(
                """
                update app_user
                set account_status = ?
                where user_id = ?
                """,
                status.name(),
                userId
        );
    }

    public int markVerifiedChefAsPending(long userId) {
        return jdbcTemplate.update(
                """
                update verified_chef_profile
                set verification_status = 'PENDING',
                    reviewed_at = null,
                    reviewed_by = null,
                    review_note = null
                where user_id = ? and verification_status = 'APPROVED'
                """,
                userId
        );
    }

    public int deleteUserById(long userId) {
        return jdbcTemplate.update("delete from app_user where user_id = ?", userId);
    }

    private long countByQuery(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private LocalDateTime toLocalDateTime(Object rawValue) {
        if (rawValue instanceof LocalDateTime value) {
            return value;
        }
        if (rawValue instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
