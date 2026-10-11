package com.testforge.backend.dto;

import java.util.List;

public record RunDetailResponse(
        RunResponse run,
        List<RunResultResponse> results) {}