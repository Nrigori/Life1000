package com.life1000.goal;

import com.life1000.entity.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GoalUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @Positive Long categoryId,
        String reason,
        GoalStatus status) {}
