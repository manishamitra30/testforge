package com.testforge.backend.dto;

import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCaseStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TestCaseResponse(
        Long id,
        Long suiteId,
        String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        String tags,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TestStepDto> steps) {}