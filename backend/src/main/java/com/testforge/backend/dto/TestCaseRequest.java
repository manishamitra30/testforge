package com.testforge.backend.dto;

import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TestCaseRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        @Size(max = 255) String tags,
        @Valid List<TestStepDto> steps) {}