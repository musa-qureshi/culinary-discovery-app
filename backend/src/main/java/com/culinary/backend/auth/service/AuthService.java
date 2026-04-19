package com.culinary.backend.auth.service;

import com.culinary.backend.auth.dto.ApproveChefRequest;
import com.culinary.backend.auth.dto.AdminDashboardStatsResponse;
import com.culinary.backend.auth.dto.AdminUserSummaryResponse;
import com.culinary.backend.auth.dto.ApprovedChefResponse;
import com.culinary.backend.auth.dto.AuthResponse;
import com.culinary.backend.auth.dto.LoginRequest;
import com.culinary.backend.auth.dto.PendingChefResponse;
import com.culinary.backend.auth.dto.RegisterRequest;
import com.culinary.backend.auth.model.AccountStatus;
import com.culinary.backend.auth.model.UserRecord;
import com.culinary.backend.auth.model.UserRole;
import com.culinary.backend.auth.repository.AuthRepository;
import com.culinary.backend.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthRepository authRepository, PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserRole role;
        try {
            role = UserRole.valueOf(request.role().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid role.");
        }

        if (role == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin accounts cannot be registered from the public form.");
        }

        if (authRepository.findUserByEmail(request.email().trim().toLowerCase()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        AccountStatus status = role == UserRole.VERIFIED_CHEF
                ? AccountStatus.PENDING_VERIFICATION
                : AccountStatus.ACTIVE;

        String normalizedEmail = request.email().trim().toLowerCase();
        String hashedPassword = passwordEncoder.encode(request.password());

        long userId = authRepository.createUser(
                request.fullName().trim(),
                normalizedEmail,
                hashedPassword,
                role,
                status
        );

        if (userId == 0L) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create user.");
        }

        if (role == UserRole.VERIFIED_CHEF) {
            String chefBio = request.chefBio() == null ? null : request.chefBio().trim();
            if (chefBio == null || chefBio.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Verified chefs must provide a bio.");
            }
            authRepository.createVerifiedChefProfile(userId, chefBio);
        }

        String message = status == AccountStatus.PENDING_VERIFICATION
                ? "Registered. Verified Chef account is blocked until admin approval."
                : "Registered successfully.";

        return new AuthResponse(userId, request.fullName().trim(), normalizedEmail, role.name(), status.name(), message);
    }

    public AuthResponse login(LoginRequest request) {
        UserRecord user = authRepository.findUserByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        boolean matches = passwordEncoder.matches(request.password(), user.passwordHash());
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        if (user.accountStatus() == AccountStatus.BLOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been blocked.");
        }

        String message = user.accountStatus() == AccountStatus.PENDING_VERIFICATION
                ? "Your Verified Chef account is pending admin verification."
                : "Login successful.";

        return new AuthResponse(
                user.userId(),
                user.fullName(),
                user.email(),
                user.role().name(),
                user.accountStatus().name(),
                message
        );
    }

    public AdminDashboardStatsResponse getDashboardStats(long adminUserId) {
        requireAdmin(adminUserId);

        return new AdminDashboardStatsResponse(
                authRepository.countTotalUsers(),
                authRepository.countUsersByRole(UserRole.HOME_COOK),
                authRepository.countUsersByRole(UserRole.SUPPLIER),
                authRepository.countUsersByRole(UserRole.VERIFIED_CHEF),
                authRepository.countUsersByRole(UserRole.ADMIN),
                authRepository.countPendingChefVerifications(),
                authRepository.countUsersByStatus(AccountStatus.ACTIVE),
                authRepository.countUsersByStatus(AccountStatus.BLOCKED)
        );
    }

    public List<AdminUserSummaryResponse> listUsers(long adminUserId, String roleFilterRaw, String searchTerm) {
        requireAdmin(adminUserId);

        UserRole roleFilter = null;
        if (roleFilterRaw != null && !roleFilterRaw.isBlank() && !"ALL".equalsIgnoreCase(roleFilterRaw.trim())) {
            try {
                roleFilter = UserRole.valueOf(roleFilterRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid role filter.");
            }
        }

        return authRepository.listUsers(roleFilter, searchTerm);
    }

    public List<PendingChefResponse> getPendingVerifiedChefs(long adminUserId) {
        requireAdmin(adminUserId);
        return authRepository.listPendingVerifiedChefs();
    }

    public List<ApprovedChefResponse> getApprovedVerifiedChefs(long adminUserId) {
        requireAdmin(adminUserId);
        return authRepository.listApprovedVerifiedChefs();
    }

    @Transactional
    public void deleteUser(long adminUserId, long targetUserId) {
        UserRecord admin = requireAdmin(adminUserId);

        UserRecord target = authRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target user not found."));

        if (target.userId() == admin.userId()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot delete your own admin account.");
        }

        if (target.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin users cannot be deleted from this endpoint.");
        }

        int deletedRows = authRepository.deleteUserById(targetUserId);
        if (deletedRows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Target user not found.");
        }
    }

    @Transactional
    public void blockUser(long adminUserId, long targetUserId) {
        UserRecord admin = requireAdmin(adminUserId);
        UserRecord target = getBlockableTargetUser(admin, targetUserId);

        if (target.accountStatus() == AccountStatus.BLOCKED) {
            return;
        }

        authRepository.updateUserStatus(targetUserId, AccountStatus.BLOCKED);
    }

    @Transactional
    public void unblockUser(long adminUserId, long targetUserId) {
        UserRecord admin = requireAdmin(adminUserId);
        UserRecord target = getBlockableTargetUser(admin, targetUserId);

        if (target.accountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pending verified chefs cannot be unblocked.");
        }

        if (target.accountStatus() == AccountStatus.ACTIVE) {
            return;
        }

        authRepository.updateUserStatus(targetUserId, AccountStatus.ACTIVE);
    }

    @Transactional
    public void markVerifiedChefPending(long adminUserId, long targetUserId) {
        requireAdmin(adminUserId);

        UserRecord chef = authRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target user not found."));

        if (chef.role() != UserRole.VERIFIED_CHEF) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target user is not a VERIFIED_CHEF.");
        }

        int updatedRows = authRepository.markVerifiedChefAsPending(targetUserId);
        if (updatedRows == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verified chef is already pending or not approved.");
        }

        authRepository.updateUserStatus(targetUserId, AccountStatus.PENDING_VERIFICATION);
    }

    @Transactional
    public AuthResponse approveVerifiedChef(long targetUserId, ApproveChefRequest request) {
        requireAdmin(request.adminUserId());

        UserRecord chef = authRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target user not found."));

        if (chef.role() != UserRole.VERIFIED_CHEF) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target user is not a VERIFIED_CHEF.");
        }

        if (chef.accountStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verified chef is not pending verification.");
        }

        authRepository.approveVerifiedChef(targetUserId, request.adminUserId(), request.reviewNote());

        return new AuthResponse(
                chef.userId(),
                chef.fullName(),
                chef.email(),
                chef.role().name(),
                AccountStatus.ACTIVE.name(),
                "Verified chef approved and account activated."
        );
    }

    private UserRecord requireAdmin(long adminUserId) {
        UserRecord admin = authRepository.findUserById(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admin user not found."));

        if (admin.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only ADMIN users can perform this action.");
        }

        return admin;
    }

    private UserRecord getBlockableTargetUser(UserRecord admin, long targetUserId) {
        UserRecord target = authRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target user not found."));

        if (target.userId() == admin.userId()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot change your own admin account status.");
        }

        if (target.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin users cannot be blocked or unblocked from this endpoint.");
        }

        if (target.role() == UserRole.VERIFIED_CHEF && target.accountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pending verified chefs cannot be blocked or unblocked.");
        }

        return target;
    }
}
