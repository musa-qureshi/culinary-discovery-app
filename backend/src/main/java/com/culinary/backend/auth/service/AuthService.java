package com.culinary.backend.auth.service;

import com.culinary.backend.auth.dto.ApproveChefRequest;
import com.culinary.backend.auth.dto.AuthResponse;
import com.culinary.backend.auth.dto.LoginRequest;
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

        if (user.accountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active. Current status: " + user.accountStatus().name());
        }

        return new AuthResponse(
                user.userId(),
                user.fullName(),
                user.email(),
                user.role().name(),
                user.accountStatus().name(),
                "Login successful."
        );
    }

    @Transactional
    public AuthResponse approveVerifiedChef(long targetUserId, ApproveChefRequest request) {
        UserRecord admin = authRepository.findUserById(request.adminUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admin user not found."));

        if (admin.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only ADMIN users can approve verified chefs.");
        }

        UserRecord chef = authRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Target user not found."));

        if (chef.role() != UserRole.VERIFIED_CHEF) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target user is not a VERIFIED_CHEF.");
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
}
