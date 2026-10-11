package com.testforge.backend.dto;

import com.testforge.backend.model.DefectStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeDefectStatusRequest(@NotNull DefectStatus status) {}