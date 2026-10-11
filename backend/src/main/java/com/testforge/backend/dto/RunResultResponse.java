package com.testforge.backend.dto;

import com.testforge.backend.model.Priority;
import com.testforge.backend.model.ResultStatus;

public record RunResultResponse(
        Long id,
        Long testCaseId,
        String title,
        Priority priority,
        ResultStatus status,
        Long durationMs,
        String comment,
        String errorMessage,
        String executedBy) {}