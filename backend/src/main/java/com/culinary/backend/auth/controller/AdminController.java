package com.culinary.backend.auth.controller;

import com.culinary.backend.auth.dto.ApproveChefRequest;
import com.culinary.backend.auth.dto.AuthResponse;
import com.culinary.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verified-chefs/{userId}/approve")
    public AuthResponse approveVerifiedChef(@PathVariable long userId, @Valid @RequestBody ApproveChefRequest request) {
        return authService.approveVerifiedChef(userId, request);
    }
}
