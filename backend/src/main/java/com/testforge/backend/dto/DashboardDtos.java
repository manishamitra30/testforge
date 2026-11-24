package com.testforge.backend.dto;

import com.testforge.backend.model.Severity;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Summary(long totalCases, long totalRuns, double passRate, long openDefects) {}

    public record PassRatePoint(String date, long passed, long executed, double passRate) {}

    public record SeverityCount(Severity severity, long count) {}

    public record Activity(String type, long refId, String title, String detail, String at) {}

    public record FlakyTest(
            long testCaseId, String title, long executions, long failures, long flips, double flakiness) {}
}