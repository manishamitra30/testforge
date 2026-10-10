package com.testforge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestSuiteRequest(
        @NotBlank @Size(max = 150) String name,
        String description) {}