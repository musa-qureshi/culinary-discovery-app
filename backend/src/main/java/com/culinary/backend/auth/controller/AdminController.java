package com.culinary.backend.auth.controller;

import com.culinary.backend.auth.dto.ApproveChefRequest;
import com.culinary.backend.auth.dto.AdminDashboardStatsResponse;
import com.culinary.backend.auth.dto.AdminUserSummaryResponse;
import com.culinary.backend.auth.dto.ApprovedChefResponse;
import com.culinary.backend.auth.dto.AuthResponse;
import com.culinary.backend.auth.dto.PendingChefResponse;
import com.culinary.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/dashboard-stats")
    public AdminDashboardStatsResponse getDashboardStats(@RequestParam long adminUserId) {
        return authService.getDashboardStats(adminUserId);
    }

    @GetMapping("/users")
    public List<AdminUserSummaryResponse> listUsers(
            @RequestParam long adminUserId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search
    ) {
        return authService.listUsers(adminUserId, role, search);
    }

    @DeleteMapping("/users/{userId}")
    public Map<String, String> deleteUser(@PathVariable long userId, @RequestParam long adminUserId) {
        authService.deleteUser(adminUserId, userId);
        return Map.of("message", "User deleted successfully.");
    }

    @PostMapping("/users/{userId}/block")
    public Map<String, String> blockUser(@PathVariable long userId, @RequestParam long adminUserId) {
        authService.blockUser(adminUserId, userId);
        return Map.of("message", "User blocked successfully.");
    }

    @PostMapping("/users/{userId}/unblock")
    public Map<String, String> unblockUser(@PathVariable long userId, @RequestParam long adminUserId) {
        authService.unblockUser(adminUserId, userId);
        return Map.of("message", "User unblocked successfully.");
    }

    @GetMapping("/verified-chefs/pending")
    public List<PendingChefResponse> getPendingVerifiedChefs(@RequestParam long adminUserId) {
        return authService.getPendingVerifiedChefs(adminUserId);
    }

    @GetMapping("/verified-chefs/approved")
    public List<ApprovedChefResponse> getApprovedVerifiedChefs(@RequestParam long adminUserId) {
        return authService.getApprovedVerifiedChefs(adminUserId);
    }

    @PostMapping("/verified-chefs/{userId}/mark-pending")
    public Map<String, String> markVerifiedChefPending(@PathVariable long userId, @RequestParam long adminUserId) {
        authService.markVerifiedChefPending(adminUserId, userId);
        return Map.of("message", "Chef moved back to pending verification.");
    }

    @PostMapping("/verified-chefs/{userId}/approve")
    public AuthResponse approveVerifiedChef(@PathVariable long userId, @Valid @RequestBody ApproveChefRequest request) {
        return authService.approveVerifiedChef(userId, request);
    }
}
