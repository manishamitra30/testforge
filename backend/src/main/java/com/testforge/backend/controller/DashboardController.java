package com.testforge.backend.controller;

import com.testforge.backend.dto.DashboardDtos.Activity;
import com.testforge.backend.dto.DashboardDtos.FlakyTest;
import com.testforge.backend.dto.DashboardDtos.PassRatePoint;
import com.testforge.backend.dto.DashboardDtos.SeverityCount;
import com.testforge.backend.dto.DashboardDtos.Summary;
import com.testforge.backend.service.DashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    public Summary summary(@RequestParam Long projectId) {
        return service.summary(projectId);
    }

    @GetMapping("/pass-rate-trend")
    public List<PassRatePoint> passRateTrend(
            @RequestParam Long projectId, @RequestParam(defaultValue = "30") int days) {
        return service.passRateTrend(projectId, days);
    }

    @GetMapping("/defects-by-severity")
    public List<SeverityCount> defectsBySeverity(@RequestParam Long projectId) {
        return service.defectsBySeverity(projectId);
    }

    @GetMapping("/recent-activity")
    public List<Activity> recentActivity(
            @RequestParam Long projectId, @RequestParam(defaultValue = "15") int limit) {
        return service.recentActivity(projectId, limit);
    }

    @GetMapping("/flaky-tests")
    public List<FlakyTest> flakyTests(
            @RequestParam Long projectId, @RequestParam(defaultValue = "10") int runs) {
        return service.flakyTests(projectId, runs);
    }
}