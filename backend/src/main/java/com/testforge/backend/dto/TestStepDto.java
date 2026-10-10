package com.testforge.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestStepDto(
        @NotNull @Min(1) Integer stepNumber,
        @NotBlank String action,
        @NotBlank String expectedResult) {}