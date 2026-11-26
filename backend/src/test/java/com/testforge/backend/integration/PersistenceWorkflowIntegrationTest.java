package com.testforge.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.dto.CreateDefectRequest;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.dto.TestStepDto;
import com.testforge.backend.dto.UpdateResultRequest;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.service.DefectService;
import com.testforge.backend.service.ProjectService;
import com.testforge.backend.service.TestCaseService;
import com.testforge.backend.service.TestRunService;
import com.testforge.backend.support.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The real services, real transactions and real PostgreSQL, with nothing mocked. These are the workflows
 * where a missing @Transactional or a wrong cascade would silently leave half-written data behind.
 */
class PersistenceWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired TestCaseService testCaseService;
    @Autowired TestRunService runService;
    @Autowired ProjectService projectService;
    @Autowired DefectService defectService;
    @Autowired TestCaseRepository testCaseRepository;

    private long ownerId;
    private long projectId;
    private long suiteId;

    private void givenAnOwnedSuite() {
        ownerId = db.user("owner");
        projectId = db.project(ownerId, "P");
        suiteId = db.suite(projectId, "S");
        loginAs("owner");
    }

    private static void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ---------- test case + steps ----------

    @Test
    void aTestCaseAndAllItsStepsAreSavedTogether() {
        givenAnOwnedSuite();
        TestCaseRequest request = new TestCaseRequest("Login", "desc", "a user exists", Priority.HIGH,
                TestCaseStatus.READY, "smoke",
                List.of(new TestStepDto(3, "verify", "visible"), new TestStepDto(1, "type", "accepted"), new TestStepDto(2, "click", "opens")));

        TestCaseResponse saved = testCaseService.create(suiteId, request);

        assertThat(db.count("test_cases")).isEqualTo(1);
        assertThat(db.count("test_steps")).isEqualTo(3);
        assertThat(saved.createdBy()).isEqualTo(ownerId);
        assertThat(saved.steps()).extracting(TestStepDto::stepNumber).containsExactly(1, 2, 3);
        var reloaded = testCaseRepository.findWithStepsById(saved.id()).orElseThrow();
        assertThat(reloaded.getSteps()).extracting(step -> step.getAction()).containsExactly("type", "click", "verify");
    }

    @Test
    void ifOneStepIsInvalidNothingOfTheTestCaseIsKept() {
        givenAnOwnedSuite();
        TestCaseRequest request = new TestCaseRequest("Half saved?", null, null, null, null, null,
                List.of(new TestStepDto(1, "fine", "fine"), new TestStepDto(2, null, "no action")));

        assertThatThrownBy(() -> testCaseService.create(suiteId, request)).isInstanceOf(RuntimeException.class);

        assertThat(db.count("test_cases")).as("test_cases").isZero();
        assertThat(db.count("test_steps")).as("test_steps").isZero();
    }

    // ---------- creating a run ----------

    @Test
    void aNewRunGetsOneNotRunResultForEveryActiveCase() {
        givenAnOwnedSuite();
        db.testCase(suiteId, "ready one", "HIGH", "READY", null);
        db.testCase(suiteId, "draft one", "LOW", "DRAFT", null);
        db.testCase(suiteId, "ready two", "MEDIUM", "READY", null);
        db.testCase(suiteId, "retired", "MEDIUM", "DEPRECATED", null);

        RunResponse run = runService.create(new CreateRunRequest(suiteId, "Regression"));

        List<String> statuses = jdbc.queryForList("select status from test_run_results where run_id = ?", String.class, run.id());
        List<String> titles = jdbc.queryForList(
                "select tc.title from test_run_results r join test_cases tc on tc.id = r.test_case_id where r.run_id = ? order by tc.id",
                String.class, run.id());
        assertThat(statuses).hasSize(3).containsOnly("NOT_RUN");
        assertThat(titles).containsExactly("ready one", "draft one", "ready two");
        assertThat(run.summary().total()).isEqualTo(3);
        assertThat(run.summary().notRun()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select status from test_runs where id = ?", String.class, run.id())).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select triggered_by from test_runs where id = ?", Long.class, run.id())).isEqualTo(ownerId);
    }

    @Test
    void aSuiteWithOnlyRetiredCasesCannotBeRun() {
        givenAnOwnedSuite();
        db.testCase(suiteId, "retired", "MEDIUM", "DEPRECATED", null);

        assertThatThrownBy(() -> runService.create(new CreateRunRequest(suiteId, "Empty")))
                .isInstanceOf(BadRequestException.class);

        assertThat(db.count("test_runs")).isZero();
        assertThat(db.count("test_run_results")).isZero();
    }

    @Test
    void aRunCannotBeCreatedForSomeoneElsesSuite() {
        givenAnOwnedSuite();
        db.testCase(suiteId, "case", "HIGH", "READY", null);
        db.user("intruder");
        loginAs("intruder");

        assertThatThrownBy(() -> runService.create(new CreateRunRequest(suiteId, "Sneaky")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(db.count("test_runs")).isZero();
    }

    // ---------- run lifecycle ----------

    @Test
    void theRunCompletesWhenTheLastResultIsRecordedAndReopensWhenOneIsReset() {
        givenAnOwnedSuite();
        db.testCase(suiteId, "one", "HIGH", "READY", null);
        db.testCase(suiteId, "two", "HIGH", "READY", null);
        RunResponse run = runService.create(new CreateRunRequest(suiteId, "Run"));
        List<Long> results = jdbc.queryForList("select id from test_run_results where run_id = ? order by id", Long.class, run.id());

        runService.updateResult(run.id(), results.get(0), new UpdateResultRequest(ResultStatus.PASSED, null, 100L));
        assertThat(runStatus(run.id())).isEqualTo("IN_PROGRESS");
        assertThat(isFinished(run.id())).isFalse();

        runService.updateResult(run.id(), results.get(1), new UpdateResultRequest(ResultStatus.FAILED, "boom", 50L));
        assertThat(runStatus(run.id())).isEqualTo("COMPLETED");
        assertThat(isFinished(run.id())).isTrue();
        assertThat(jdbc.queryForList("select executed_by from test_run_results where run_id = ?", Long.class, run.id()))
                .containsOnly(ownerId);

        runService.updateResult(run.id(), results.get(1), new UpdateResultRequest(ResultStatus.NOT_RUN, null, null));
        assertThat(runStatus(run.id())).isEqualTo("IN_PROGRESS");
        assertThat(isFinished(run.id())).isFalse();
        assertThat(jdbc.queryForObject("select executed_by from test_run_results where id = ?", Long.class, results.get(1))).isNull();
    }

    private String runStatus(long runId) {
        return jdbc.queryForObject("select status from test_runs where id = ?", String.class, runId);
    }

    private boolean isFinished(long runId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select finished_at is not null from test_runs where id = ?", Boolean.class, runId));
    }

    // ---------- defects ----------

    @Test
    void onlyOneNonClosedDefectCanExistPerFailedResult() {
        givenAnOwnedSuite();
        long testCase = db.testCase(suiteId, "case", "HIGH", "READY", null);
        long run = db.run(projectId, suiteId, "Run", "COMPLETED", 1, ownerId);
        long failed = db.result(run, testCase, "FAILED");

        DefectResponse first = defectService.create(new CreateDefectRequest(failed, null, null, null, null));
        assertThatThrownBy(() -> defectService.create(new CreateDefectRequest(failed, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        assertThat(db.count("defects")).isEqualTo(1);

        defectService.changeStatus(first.id(), DefectStatus.CLOSED);
        defectService.create(new CreateDefectRequest(failed, null, null, null, null));

        assertThat(db.count("defects")).isEqualTo(2);
    }

    // ---------- deleting a project ----------

    @Test
    void deletingAProjectThroughTheServiceRemovesItsWholeTreeAndNothingElse() {
        givenAnOwnedSuite();
        long testCase = db.testCase(suiteId, "case", "HIGH", "READY", null);
        db.step(testCase, 1, "do", "see");
        long run = db.run(projectId, suiteId, "Run", "COMPLETED", 1, ownerId);
        long result = db.result(run, testCase, "FAILED");
        db.defect(projectId, result, "Bug", "HIGH", "OPEN", ownerId);

        long otherOwner = db.user("other");
        long otherProject = db.project(otherOwner, "Keep me");
        long otherSuite = db.suite(otherProject, "Keep suite");
        db.testCase(otherSuite, "keep case", "LOW", "READY", null);

        projectService.delete(projectId);

        assertThat(db.count("projects")).isEqualTo(1);
        assertThat(db.count("test_suites")).isEqualTo(1);
        assertThat(db.count("test_cases")).isEqualTo(1);
        for (String table : List.of("test_steps", "test_runs", "test_run_results", "defects")) {
            assertThat(db.count(table)).as(table).isZero();
        }
    }

    // ---------- searching test cases ----------

    private void givenSearchableCases() {
        givenAnOwnedSuite();
        db.testCase(suiteId, "Login works", "HIGH", "READY", "smoke,auth");
        db.testCase(suiteId, "Logout works", "LOW", "READY", "auth");
        db.testCase(suiteId, "Checkout total", "HIGH", "DRAFT", "payments");
        db.testCase(suiteId, "LOGIN lockout", "MEDIUM", "READY", null);
        long otherProject = db.project(db.user("other"), "Other");
        db.testCase(db.suite(otherProject, "OS"), "Login elsewhere", "HIGH", "READY", null);
    }

    private List<String> search(Priority priority, TestCaseStatus status, String q) {
        return testCaseService.search(projectId, priority, status, q, PageRequest.of(0, 20, Sort.by("id")))
                .getContent().stream().map(TestCaseSummaryResponse::title).toList();
    }

    @Test
    void searchMatchesTitlesCaseInsensitively() {
        givenSearchableCases();

        assertThat(search(null, null, "login")).containsExactlyInAnyOrder("Login works", "LOGIN lockout");
    }

    @Test
    void searchAlsoMatchesTags() {
        givenSearchableCases();

        assertThat(search(null, null, "auth")).containsExactlyInAnyOrder("Login works", "Logout works");
    }

    @Test
    void searchCombinesPriorityStatusAndText() {
        givenSearchableCases();

        assertThat(search(Priority.HIGH, null, null)).containsExactlyInAnyOrder("Login works", "Checkout total");
        assertThat(search(Priority.HIGH, TestCaseStatus.DRAFT, null)).containsExactly("Checkout total");
        assertThat(search(null, TestCaseStatus.READY, null)).hasSize(3);
        assertThat(search(Priority.LOW, null, "login")).isEmpty();
    }

    @Test
    void searchNeverReturnsCasesOfOtherProjects() {
        givenSearchableCases();

        assertThat(search(null, null, null)).hasSize(4).doesNotContain("Login elsewhere");
    }

    @Test
    void searchPagesAndSortsInTheDatabase() {
        givenSearchableCases();

        Page<TestCaseSummaryResponse> first = testCaseService.search(projectId, null, null, null, PageRequest.of(0, 3, Sort.by("id")));
        Page<TestCaseSummaryResponse> second = testCaseService.search(projectId, null, null, null, PageRequest.of(1, 3, Sort.by("id")));
        Page<TestCaseSummaryResponse> byTitle = testCaseService.search(projectId, null, null, null, PageRequest.of(0, 1, Sort.by("title").ascending()));

        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(TestCaseSummaryResponse::title)
                .containsExactly("Login works", "Logout works", "Checkout total");
        assertThat(second.getContent()).extracting(TestCaseSummaryResponse::title).containsExactly("LOGIN lockout");
        assertThat(byTitle.getContent()).hasSize(1);
    }

    @Test
    void searchIsRefusedForSomeoneElsesProject() {
        givenSearchableCases();
        db.user("intruder");
        loginAs("intruder");

        assertThatThrownBy(() -> search(null, null, null)).isInstanceOf(AccessDeniedException.class);
    }
}
