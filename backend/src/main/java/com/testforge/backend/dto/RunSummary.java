package com.testforge.backend.dto;

public record RunSummary(
        long total,
        long passed,
        long failed,
        long blocked,
        long skipped,
        long notRun,
        double passRate) {}