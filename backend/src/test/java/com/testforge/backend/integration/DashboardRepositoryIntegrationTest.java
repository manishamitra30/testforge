package com.testforge.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.testforge.backend.model.Severity;
import com.testforge.backend.repository.DashboardRepository;
import com.testforge.backend.support.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Runs the dashboard's native and JPQL aggregates against real PostgreSQL (FILTER, lag() and to_char are
 * PostgreSQL features that an in-memory database would not give the same answers for).
 *
 * Project P holds the data under test; project Q holds unrelated data that must never leak into P's numbers.
 *
 *   run   age       status       A (flaky)   B (stable)   C (always fails)
 *   r0    60 days   COMPLETED    -           PASSED       -
 *   r1    3 days    COMPLETED    PASSED      PASSED       FAILED
 *   r2    2 days    COMPLETED    FAILED      PASSED       FAILED
 *   r3    1 day     COMPLETED    PASSED      PASSED       FAILED
 *   r4    today     IN_PROGRESS  NOT_RUN     NOT_RUN      NOT_RUN
 */
class DashboardRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    DashboardRepository repository;

    private long projectP;
    private long projectQ;

    @BeforeEach
    void seed() {
        long owner = db.user("owner");
        long other = db.user("other");

        projectP = db.project(owner, "P");
        long suite = db.suite(projectP, "S");
        long a = db.testCase(suite, "A flaky", "HIGH", "READY", null);
        long b = db.testCase(suite, "B stable", "MEDIUM", "READY", null);
        long c = db.testCase(suite, "C broken", "LOW", "READY", null);

        long r0 = db.run(projectP, suite, "r0", "COMPLETED", 60, owner);
        db.result(r0, b, "PASSED");
        long r1 = db.run(projectP, suite, "r1", "COMPLETED", 3, owner);
        db.result(r1, a, "PASSED");
        db.result(r1, b, "PASSED");
        db.result(r1, c, "FAILED");
        long r2 = db.run(projectP, suite, "r2", "COMPLETED", 2, owner);
        db.result(r2, a, "FAILED");
        db.result(r2, b, "PASSED");
        db.result(r2, c, "FAILED");
        long r3 = db.run(projectP, suite, "r3", "COMPLETED", 1, owner);
        db.result(r3, a, "PASSED");
        db.result(r3, b, "PASSED");
        db.result(r3, c, "FAILED");
        long r4 = db.run(projectP, suite, "r4", "IN_PROGRESS", 0, owner);
        db.result(r4, a, "NOT_RUN");
        db.result(r4, b, "NOT_RUN");
        db.result(r4, c, "NOT_RUN");

        db.defect(projectP, null, "open high", "HIGH", "OPEN", owner);
        db.defect(projectP, null, "in progress high", "HIGH", "IN_PROGRESS", owner);
        db.defect(projectP, null, "resolved low", "LOW", "RESOLVED", owner);
        db.defect(projectP, null, "closed critical", "CRITICAL", "CLOSED", owner);

        projectQ = db.project(other, "Q");
        long suiteQ = db.suite(projectQ, "SQ");
        long caseQ = db.testCase(suiteQ, "Q case", "HIGH", "READY", null);
        long runQ = db.run(projectQ, suiteQ, "q run", "COMPLETED", 1, other);
        db.result(runQ, caseQ, "FAILED");
        db.defect(projectQ, null, "q defect", "CRITICAL", "OPEN", other);
    }

    @Test
    void countsAreScopedToTheProject() {
        assertThat(repository.countCases(projectP)).isEqualTo(3);
        assertThat(repository.countCases(projectQ)).isEqualTo(1);
        assertThat(repository.countRuns(projectP)).isEqualTo(5);
        assertThat(repository.countRuns(projectQ)).isEqualTo(1);
    }

    @Test
    void passRateInputsIgnoreNotRunResults() {
        assertThat(repository.countPassed(projectP)).isEqualTo(6);
        assertThat(repository.countExecuted(projectP)).isEqualTo(10); // 13 results, 3 of them NOT_RUN
        assertThat(repository.countPassed(projectQ)).isZero();
        assertThat(repository.countExecuted(projectQ)).isEqualTo(1);
    }

    @Test
    void openDefectsAreOpenPlusInProgressOnly() {
        assertThat(repository.countOpenDefects(projectP)).isEqualTo(2);
        assertThat(repository.countOpenDefects(projectQ)).isEqualTo(1);
    }

    @Test
    void defectsAreGroupedBySeverityPerProject() {
        Map<Severity, Long> counts = repository.countDefectsBySeverity(projectP).stream()
                .collect(Collectors.toMap(DashboardRepository.SeverityTotal::getSeverity, DashboardRepository.SeverityTotal::getTotal));

        assertThat(counts)
                .containsEntry(Severity.HIGH, 2L)
                .containsEntry(Severity.LOW, 1L)
                .containsEntry(Severity.CRITICAL, 1L)
                .doesNotContainKey(Severity.MEDIUM);
    }

    @Test
    void trendHasOneRowPerDayWithExecutedResultsInDateOrder() {
        var since = LocalDate.now().minusDays(10).atStartOfDay();

        List<DashboardRepository.DayRow> days = repository.passRateByDay(projectP, since);

        // r0 is older than the window, and r4 has nothing executed, so only r1..r3 remain.
        assertThat(days).hasSize(3);
        assertThat(days).extracting(DashboardRepository.DayRow::getExecuted).containsExactly(3L, 3L, 3L);
        assertThat(days).extracting(DashboardRepository.DayRow::getPassed).containsExactly(2L, 1L, 2L);
        assertThat(days).extracting(DashboardRepository.DayRow::getDay)
                .allMatch(day -> day.matches("\\d{4}-\\d{2}-\\d{2}"))
                .isSorted();
    }

    @Test
    void aWiderWindowAlsoIncludesTheOldRun() {
        var since = LocalDate.now().minusDays(90).atStartOfDay();

        assertThat(repository.passRateByDay(projectP, since)).hasSize(4);
    }

    @Test
    void flakyTestsAreThoseThatFlipBetweenPassAndFail() {
        List<DashboardRepository.FlakyRow> flaky = repository.flakyTests(projectP, 10);

        assertThat(flaky).singleElement().satisfies(row -> {
            assertThat(row.getTitle()).isEqualTo("A flaky");
            assertThat(row.getExecutions()).isEqualTo(3); // r1, r2, r3 (r4 is unfinished and NOT_RUN)
            assertThat(row.getFailures()).isEqualTo(1);
            assertThat(row.getFlips()).isEqualTo(2); // PASSED->FAILED, FAILED->PASSED
        });
    }

    @Test
    void aTestThatAlwaysFailsOrAlwaysPassesIsNotFlaky() {
        assertThat(repository.flakyTests(projectP, 10)).extracting(DashboardRepository.FlakyRow::getTitle)
                .doesNotContain("B stable", "C broken");
    }

    @Test
    void theFlakyWindowOnlyLooksAtTheLastNFinishedRuns() {
        List<DashboardRepository.FlakyRow> flaky = repository.flakyTests(projectP, 2); // r3 and r2

        assertThat(flaky).singleElement().satisfies(row -> {
            assertThat(row.getExecutions()).isEqualTo(2);
            assertThat(row.getFlips()).isEqualTo(1);
        });
    }

    @Test
    void flakyTestsOfOtherProjectsAreNeverMixedIn() {
        assertThat(repository.flakyTests(projectQ, 10)).isEmpty();
    }

    @Test
    void recentActivityMixesRunsAndDefectsNewestFirst() {
        List<DashboardRepository.ActivityRow> all = repository.recentActivity(projectP, 100);

        assertThat(all).hasSize(9); // 5 runs + 4 defects
        assertThat(all).extracting(DashboardRepository.ActivityRow::getType).contains("RUN", "DEFECT");

        List<String> times = all.stream().map(DashboardRepository.ActivityRow::getAt).collect(Collectors.toCollection(ArrayList::new));
        List<String> newestFirst = new ArrayList<>(times);
        newestFirst.sort(Collections.reverseOrder());
        assertThat(times).isEqualTo(newestFirst);
    }

    @Test
    void recentActivityHonoursTheLimitAndIsScopedToTheProject() {
        assertThat(repository.recentActivity(projectP, 3)).hasSize(3);
        assertThat(repository.recentActivity(projectQ, 100)).hasSize(2); // one run, one defect
    }

    @Test
    void recentActivityDescribesRunsByStatusAndDefectsBySeverityAndStatus() {
        List<DashboardRepository.ActivityRow> all = repository.recentActivity(projectP, 100);

        assertThat(all).filteredOn(row -> row.getType().equals("RUN"))
                .extracting(DashboardRepository.ActivityRow::getDetail)
                .contains("IN_PROGRESS", "COMPLETED");
        assertThat(all).filteredOn(row -> row.getType().equals("DEFECT"))
                .extracting(DashboardRepository.ActivityRow::getDetail)
                .contains("HIGH / OPEN", "CRITICAL / CLOSED");
    }
}
