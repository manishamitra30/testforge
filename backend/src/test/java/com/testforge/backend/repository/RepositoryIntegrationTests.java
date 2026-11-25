package com.testforge.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.testforge.backend.AbstractIntegrationTest;
import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestStep;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class RepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestSuiteRepository testSuiteRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestRunResultRepository testRunResultRepository;
    @Autowired private DefectRepository defectRepository;
    @Autowired private DashboardRepository dashboardRepository;

    private User createUser(String suffix) {
        return userRepository.save(User.builder()
                .username("qa_" + suffix)
                .email("qa_" + suffix + "@testforge.local")
                .password("$2a$10$encodedPasswordHashForTest")
                .role("USER")
                .build());
    }

    private Project createProject(User owner, String name) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("Integration test project");
        project.setOwner(owner);
        return projectRepository.save(project);
    }

    private TestSuite createSuite(Project project, String name) {
        TestSuite suite = new TestSuite();
        suite.setProject(project);
        suite.setName(name);
        suite.setDescription("Suite description");
        return testSuiteRepository.save(suite);
    }

    private TestCase createTestCaseWithSteps(TestSuite suite, User creator, String title, TestCaseStatus status) {
        TestCase tc = new TestCase();
        tc.setSuite(suite);
        tc.setTitle(title);
        tc.setDescription("Case description");
        tc.setPreconditions("User logged in");
        tc.setPriority(Priority.HIGH);
        tc.setStatus(status);
        tc.setTags("smoke,api");
        tc.setCreatedBy(creator);

        TestStep s1 = new TestStep();
        s1.setStepNumber(1);
        s1.setAction("Send POST /api/auth/login");
        s1.setExpectedResult("200 OK with JWT");

        TestStep s2 = new TestStep();
        s2.setStepNumber(2);
        s2.setAction("Call protected endpoint");
        s2.setExpectedResult("200 OK");

        tc.replaceSteps(List.of(s1, s2));
        return testCaseRepository.save(tc);
    }

    @Test
    void shouldPersistTestCaseWithStepsAndReplaceOrphanSteps() {
        User owner = createUser("tc_steps");
        Project project = createProject(owner, "Auth Service");
        TestSuite suite = createSuite(project, "Smoke Suite");

        TestCase saved = createTestCaseWithSteps(suite, owner, "Login flow", TestCaseStatus.READY);

        Optional<TestCase> loaded = testCaseRepository.findWithStepsById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getSteps()).hasSize(2);
        assertThat(loaded.get().getSteps().get(0).getStepNumber()).isEqualTo(1);
        assertThat(loaded.get().getSteps().get(1).getStepNumber()).isEqualTo(2);

        TestStep replacement = new TestStep();
        replacement.setStepNumber(1);
        replacement.setAction("Single step flow");
        replacement.setExpectedResult("Pass");

        TestCase toUpdate = loaded.get();
        toUpdate.replaceSteps(List.of(replacement));
        testCaseRepository.saveAndFlush(toUpdate);

        TestCase reloaded = testCaseRepository.findWithStepsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSteps()).hasSize(1);
        assertThat(reloaded.getSteps().get(0).getAction()).isEqualTo("Single step flow");
    }

    @Test
    void shouldFilterTestCasesBySuiteAndExcludeDeprecatedStatus() {
        User owner = createUser("tc_filter");
        Project project = createProject(owner, "Filter Project");
        TestSuite suite = createSuite(project, "Regression Suite");

        createTestCaseWithSteps(suite, owner, "Active Case", TestCaseStatus.READY);
        createTestCaseWithSteps(suite, owner, "Deprecated Case", TestCaseStatus.DEPRECATED);

        List<TestCase> activeCases = testCaseRepository
                .findBySuiteIdAndStatusNotOrderByIdAsc(suite.getId(), TestCaseStatus.DEPRECATED);
        assertThat(activeCases).extracting(TestCase::getTitle).containsExactly("Active Case");

        Page<TestCase> page = testCaseRepository.findBySuiteId(
                suite.getId(), PageRequest.of(0, 10, Sort.by("id").ascending()));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldAggregateRunResultsAndDetectFlakyTestsInDashboardRepository() {
        User owner = createUser("dash_flaky");
        Project project = createProject(owner, "Payments API");
        TestSuite suite = createSuite(project, "Checkout Suite");

        TestCase flakyCase = createTestCaseWithSteps(suite, owner, "Card Charge Retry", TestCaseStatus.READY);
        TestCase stableCase = createTestCaseWithSteps(suite, owner, "Health Check", TestCaseStatus.READY);

        TestRun run1 = new TestRun();
        run1.setProject(project);
        run1.setSuite(suite);
        run1.setName("Run #1");
        run1.setType(RunType.AUTOMATED);
        run1.setStatus(RunStatus.COMPLETED);
        run1.setTriggeredBy(owner);
        run1 = testRunRepository.save(run1);

        TestRun run2 = new TestRun();
        run2.setProject(project);
        run2.setSuite(suite);
        run2.setName("Run #2");
        run2.setType(RunType.AUTOMATED);
        run2.setStatus(RunStatus.FAILED);
        run2.setTriggeredBy(owner);
        run2 = testRunRepository.save(run2);

        TestRunResult r1Flaky = new TestRunResult();
        r1Flaky.setRun(run1);
        r1Flaky.setTestCase(flakyCase);
        r1Flaky.setStatus(ResultStatus.PASSED);
        r1Flaky.setDurationMs(120L);
        r1Flaky.setExecutedBy(owner);

        TestRunResult r1Stable = new TestRunResult();
        r1Stable.setRun(run1);
        r1Stable.setTestCase(stableCase);
        r1Stable.setStatus(ResultStatus.PASSED);
        r1Stable.setDurationMs(30L);
        r1Stable.setExecutedBy(owner);

        TestRunResult r2Flaky = new TestRunResult();
        r2Flaky.setRun(run2);
        r2Flaky.setTestCase(flakyCase);
        r2Flaky.setStatus(ResultStatus.FAILED);
        r2Flaky.setDurationMs(450L);
        r2Flaky.setErrorMessage("Timeout");
        r2Flaky.setExecutedBy(owner);

        TestRunResult r2Stable = new TestRunResult();
        r2Stable.setRun(run2);
        r2Stable.setTestCase(stableCase);
        r2Stable.setStatus(ResultStatus.PASSED);
        r2Stable.setDurationMs(25L);
        r2Stable.setExecutedBy(owner);

        testRunResultRepository.saveAll(List.of(r1Flaky, r1Stable, r2Flaky, r2Stable));

        Defect openHigh = new Defect();
        openHigh.setProject(project);
        openHigh.setTestResult(r2Flaky);
        openHigh.setTitle("Gateway timeout on retry");
        openHigh.setSeverity(Severity.HIGH);
        openHigh.setStatus(DefectStatus.OPEN);
        openHigh.setReporter(owner);
        defectRepository.save(openHigh);

        Defect closedMedium = new Defect();
        closedMedium.setProject(project);
        closedMedium.setTitle("Old log formatting");
        closedMedium.setSeverity(Severity.MEDIUM);
        closedMedium.setStatus(DefectStatus.CLOSED);
        closedMedium.setReporter(owner);
        defectRepository.save(closedMedium);

        assertThat(dashboardRepository.countCases(project.getId())).isEqualTo(2);
        assertThat(dashboardRepository.countRuns(project.getId())).isEqualTo(2);
        assertThat(dashboardRepository.countPassed(project.getId())).isEqualTo(3);
        assertThat(dashboardRepository.countExecuted(project.getId())).isEqualTo(4);
        assertThat(dashboardRepository.countOpenDefects(project.getId())).isEqualTo(1);
        assertThat(defectRepository.existsByTestResultIdAndStatusNot(r2Flaky.getId(), DefectStatus.CLOSED))
                .isTrue();

        Map<Severity, Long> severityTotals = dashboardRepository.countDefectsBySeverity(project.getId())
                .stream()
                .collect(Collectors.toMap(
                        DashboardRepository.SeverityTotal::getSeverity,
                        DashboardRepository.SeverityTotal::getTotal));
        assertThat(severityTotals.get(Severity.HIGH)).isEqualTo(1L);
        assertThat(severityTotals.get(Severity.MEDIUM)).isEqualTo(1L);

        List<TestRunResultRepository.RunStatusCount> runCounts =
                testRunResultRepository.countByRunIds(List.of(run1.getId(), run2.getId()));
        assertThat(runCounts).isNotEmpty();

        List<DashboardRepository.FlakyRow> flakyRows = dashboardRepository.flakyTests(project.getId(), 5);
        assertThat(flakyRows).hasSize(1);
        assertThat(flakyRows.get(0).getTestCaseId()).isEqualTo(flakyCase.getId());
        assertThat(flakyRows.get(0).getFlips()).isEqualTo(1L);

        List<DashboardRepository.DayRow> trend =
                dashboardRepository.passRateByDay(project.getId(), LocalDateTime.now().minusDays(1));
        assertThat(trend).isNotEmpty();

        assertThat(dashboardRepository.recentActivity(project.getId(), 10)).isNotEmpty();
    }
}