package com.testforge.backend.dto;

import com.testforge.backend.model.ResultStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateResultRequest(
        @NotNull ResultStatus status,
        @Size(max = 5000) String comment,
        @PositiveOrZero Long durationMs) {}