package com.testforge.backend.dto;

import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCaseStatus;
import java.time.LocalDateTime;

public record TestCaseSummaryResponse(
        Long id,
        Long suiteId,
        String title,
        Priority priority,
        TestCaseStatus status,
        String tags,
        LocalDateTime updatedAt) {}