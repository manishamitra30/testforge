package com.testforge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRunRequest(
        @NotNull Long suiteId,
        @NotBlank @Size(max = 150) String name) {}