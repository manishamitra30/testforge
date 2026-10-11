package com.testforge.backend.dto;

import com.testforge.backend.model.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDefectRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull Severity severity,
        Long assigneeId) {}