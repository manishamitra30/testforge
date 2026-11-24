package com.testforge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testforge.backend.dto.DashboardDtos.FlakyTest;
import com.testforge.backend.dto.DashboardDtos.PassRatePoint;
import com.testforge.backend.dto.DashboardDtos.SeverityCount;
import com.testforge.backend.dto.DashboardDtos.Summary;
import com.testforge.backend.model.Severity;
import com.testforge.backend.repository.DashboardRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceTest {

    @Mock DashboardRepository repository;
    @InjectMocks DashboardQueryService service;

    @Test
    void summary_combinesTheCountsAndRoundsThePassRateToOneDecimal() {
        when(repository.countPassed(1L)).thenReturn(22L);
        when(repository.countExecuted(1L)).thenReturn(30L);
        when(repository.countCases(1L)).thenReturn(6L);
        when(repository.countRuns(1L)).thenReturn(5L);
        when(repository.countOpenDefects(1L)).thenReturn(2L);

        Summary summary = service.summary(1L);

        assertThat(summary).isEqualTo(new Summary(6, 5, 73.3, 2));
    }

    @Test
    void summary_reportsAZeroPassRateInsteadOfDividingByZeroWhenNothingRan() {
        when(repository.countPassed(1L)).thenReturn(0L);
        when(repository.countExecuted(1L)).thenReturn(0L);

        assertThat(service.summary(1L).passRate()).isZero();
    }

    @Test
    void passRateTrend_asksForTheLastNDaysStartingAtMidnightAndComputesEachDaysRate() {
        when(repository.passRateByDay(eq(1L), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(day("2026-11-23", 5, 6), day("2026-11-24", 17, 24)));

        List<PassRatePoint> trend = service.passRateTrend(1L, 30);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).passRateByDay(eq(1L), since.capture());
        assertThat(since.getValue()).isEqualTo(LocalDate.now().minusDays(29).atStartOfDay());
        assertThat(trend).extracting(PassRatePoint::passRate).containsExactly(83.3, 70.8);
        assertThat(trend).extracting(PassRatePoint::date).containsExactly("2026-11-23", "2026-11-24");
    }

    @Test
    void defectsBySeverity_alwaysReturnsAllFourSeveritiesInOrderWithZerosFilledIn() {
        when(repository.countDefectsBySeverity(1L)).thenReturn(List.of(severity(Severity.HIGH, 3), severity(Severity.LOW, 1)));

        List<SeverityCount> counts = service.defectsBySeverity(1L);

        assertThat(counts).containsExactly(
                new SeverityCount(Severity.LOW, 1),
                new SeverityCount(Severity.MEDIUM, 0),
                new SeverityCount(Severity.HIGH, 3),
                new SeverityCount(Severity.CRITICAL, 0));
    }

    @Test
    void flakyTests_computesFlakinessAsFlipsPerOpportunityToFlip() {
        when(repository.flakyTests(1L, 10)).thenReturn(List.of(flaky(7L, "Login", 5, 1, 2)));

        List<FlakyTest> result = service.flakyTests(1L, 10);

        assertThat(result).singleElement().satisfies(test -> {
            assertThat(test.testCaseId()).isEqualTo(7L);
            assertThat(test.title()).isEqualTo("Login");
            assertThat(test.failures()).isEqualTo(1);
            assertThat(test.flips()).isEqualTo(2);
            assertThat(test.flakiness()).isEqualTo(50.0);
        });
    }

    @Test
    void flakyTests_neverDividesByZeroForASingleExecution() {
        when(repository.flakyTests(1L, 10)).thenReturn(List.of(flaky(8L, "Once", 1, 0, 0)));

        assertThat(service.flakyTests(1L, 10)).singleElement().extracting(FlakyTest::flakiness).isEqualTo(0.0);
    }

    // ---------- row stubs ----------

    private static DashboardRepository.DayRow day(String date, long passed, long executed) {
        return new DashboardRepository.DayRow() {
            @Override public String getDay() { return date; }
            @Override public long getPassed() { return passed; }
            @Override public long getExecuted() { return executed; }
        };
    }

    private static DashboardRepository.SeverityTotal severity(Severity severity, long total) {
        return new DashboardRepository.SeverityTotal() {
            @Override public Severity getSeverity() { return severity; }
            @Override public long getTotal() { return total; }
        };
    }

    private static DashboardRepository.FlakyRow flaky(long id, String title, long executions, long failures, long flips) {
        return new DashboardRepository.FlakyRow() {
            @Override public long getTestCaseId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public long getExecutions() { return executions; }
            @Override public long getFailures() { return failures; }
            @Override public long getFlips() { return flips; }
        };
    }
}