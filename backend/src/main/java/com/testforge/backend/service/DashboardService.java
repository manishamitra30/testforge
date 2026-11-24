package com.testforge.backend.service;

import com.testforge.backend.dto.DashboardDtos.Activity;
import com.testforge.backend.dto.DashboardDtos.FlakyTest;
import com.testforge.backend.dto.DashboardDtos.PassRatePoint;
import com.testforge.backend.dto.DashboardDtos.SeverityCount;
import com.testforge.backend.dto.DashboardDtos.Summary;
import com.testforge.backend.repository.DashboardRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectService projectService;
    private final DashboardQueryService queries;
    private final DashboardRepository repository;

    /** Access check runs on every request, even when the data itself comes from the cache. */
    private void check(Long projectId) {
        projectService.getOwnedProject(projectId);
    }

    public Summary summary(Long projectId) {
        check(projectId);
        return queries.summary(projectId);
    }

    public List<PassRatePoint> passRateTrend(Long projectId, int days) {
        check(projectId);
        return queries.passRateTrend(projectId, Math.max(1, Math.min(days, 365)));
    }

    public List<SeverityCount> defectsBySeverity(Long projectId) {
        check(projectId);
        return queries.defectsBySeverity(projectId);
    }

    @Transactional(readOnly = true)
    public List<Activity> recentActivity(Long projectId, int limit) {
        check(projectId);
        return repository.recentActivity(projectId, Math.max(1, Math.min(limit, 50))).stream()
                .map(a -> new Activity(a.getType(), a.getRefId(), a.getTitle(), a.getDetail(), a.getAt()))
                .toList();
    }

    public List<FlakyTest> flakyTests(Long projectId, int runs) {
        check(projectId);
        return queries.flakyTests(projectId, Math.max(2, Math.min(runs, 50)));
    }
}