package com.testforge.backend.dto;

import com.testforge.backend.model.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDefectRequest(
        @NotNull Long testResultId,
        @Size(max = 255) String title,
        String description,
        Severity severity,
        Long assigneeId) {}