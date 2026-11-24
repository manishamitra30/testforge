package com.testforge.backend.service;

import com.testforge.backend.config.CacheConfig;
import com.testforge.backend.dto.DashboardDtos.FlakyTest;
import com.testforge.backend.dto.DashboardDtos.PassRatePoint;
import com.testforge.backend.dto.DashboardDtos.SeverityCount;
import com.testforge.backend.dto.DashboardDtos.Summary;
import com.testforge.backend.model.Severity;
import com.testforge.backend.repository.DashboardRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cached aggregate queries. Kept in its own bean so the cache proxy is always used. It does NOT check
 * access: DashboardService verifies project ownership before calling it.
 */
@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final DashboardRepository repository;

    private static double rate(long passed, long executed) {
        return executed == 0 ? 0.0 : Math.round(passed * 1000.0 / executed) / 10.0;
    }

    @Cacheable(cacheNames = CacheConfig.SUMMARY, key = "#projectId")
    @Transactional(readOnly = true)
    public Summary summary(Long projectId) {
        long passed = repository.countPassed(projectId);
        long executed = repository.countExecuted(projectId);
        return new Summary(
                repository.countCases(projectId),
                repository.countRuns(projectId),
                rate(passed, executed),
                repository.countOpenDefects(projectId));
    }

    @Cacheable(cacheNames = CacheConfig.TREND, key = "#projectId + ':' + #days")
    @Transactional(readOnly = true)
    public List<PassRatePoint> passRateTrend(Long projectId, int days) {
        var since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        return repository.passRateByDay(projectId, since).stream()
                .map(d -> new PassRatePoint(d.getDay(), d.getPassed(), d.getExecuted(), rate(d.getPassed(), d.getExecuted())))
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.SEVERITY, key = "#projectId")
    @Transactional(readOnly = true)
    public List<SeverityCount> defectsBySeverity(Long projectId) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) counts.put(s, 0L);
        repository.countDefectsBySeverity(projectId).forEach(row -> counts.put(row.getSeverity(), row.getTotal()));
        return counts.entrySet().stream().map(e -> new SeverityCount(e.getKey(), e.getValue())).toList();
    }

    @Cacheable(cacheNames = CacheConfig.FLAKY, key = "#projectId + ':' + #runs")
    @Transactional(readOnly = true)
    public List<FlakyTest> flakyTests(Long projectId, int runs) {
        return repository.flakyTests(projectId, runs).stream()
                .map(f -> new FlakyTest(
                        f.getTestCaseId(),
                        f.getTitle(),
                        f.getExecutions(),
                        f.getFailures(),
                        f.getFlips(),
                        f.getExecutions() < 2 ? 0.0 : Math.round(f.getFlips() * 1000.0 / (f.getExecutions() - 1)) / 10.0))
                .toList();
    }
}