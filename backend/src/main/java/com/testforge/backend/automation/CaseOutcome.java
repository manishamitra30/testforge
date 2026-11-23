package com.testforge.backend.automation;

import com.testforge.backend.model.ResultStatus;

public record CaseOutcome(ResultStatus status, long durationMs, String errorMessage) {
}