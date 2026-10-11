package com.testforge.backend.dto;

import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import java.time.LocalDateTime;

public record RunResponse(
        Long id,
        Long projectId,
        Long suiteId,
        String suiteName,
        String name,
        RunType type,
        RunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String triggeredBy,
        RunSummary summary) {}