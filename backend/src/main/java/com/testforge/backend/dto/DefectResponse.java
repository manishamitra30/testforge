package com.testforge.backend.dto;

import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Severity;
import java.time.LocalDateTime;
import java.util.List;

public record DefectResponse(
        Long id,
        Long projectId,
        Long testResultId,
        Long runId,
        String runName,
        Long testCaseId,
        String testCaseTitle,
        String title,
        String description,
        Severity severity,
        DefectStatus status,
        Long assigneeId,
        String assigneeName,
        Long reporterId,
        String reporterName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DefectStatus> allowedTransitions) {}