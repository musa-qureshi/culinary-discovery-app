package com.culinary.backend.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApproveChefRequest(
        @NotNull Long adminUserId,
        @Size(max = 255) String reviewNote
) {
}
