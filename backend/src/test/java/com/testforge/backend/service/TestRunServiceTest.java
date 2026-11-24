package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.project;
import static com.testforge.backend.support.TestData.result;
import static com.testforge.backend.support.TestData.run;
import static com.testforge.backend.support.TestData.suite;
import static com.testforge.backend.support.TestData.testCase;
import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunDetailResponse;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunResultResponse;
import com.testforge.backend.dto.UpdateResultRequest;
import com.testforge.backend.mapper.TestRunMapper;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.repository.TestRunRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TestRunServiceTest {

    @Mock TestRunRepository runRepository;
    @Mock TestRunResultRepository resultRepository;
    @Mock TestCaseRepository testCaseRepository;
    @Mock TestSuiteService suiteService;
    @Mock ProjectService projectService;
    @Mock CurrentUserService currentUserService;
    @Spy TestRunMapper mapper = new TestRunMapper();
    @InjectMocks TestRunService service;

    @Captor ArgumentCaptor<List<TestRunResult>> resultsCaptor;

    private final User tester = user(1L, "manisha");
    private final Project project = project(1L, tester);
    private final TestSuite suite = suite(3L, project);

    // ---------- create ----------

    @Test
    void create_makesOneNotRunResultForEveryActiveCaseInTheSuite() {
        List<TestCase> cases = List.of(
                testCase(1L, suite, "Login", Priority.HIGH),
                testCase(2L, suite, "Logout", Priority.LOW),
                testCase(3L, suite, "Reset password", Priority.MEDIUM));
        when(suiteService.getOwnedSuite(3L)).thenReturn(suite);
        when(testCaseRepository.findBySuiteIdAndStatusNotOrderByIdAsc(3L, TestCaseStatus.DEPRECATED)).thenReturn(cases);
        when(currentUserService.getCurrentUser()).thenReturn(tester);

        RunResponse response = service.create(new CreateRunRequest(3L, "  Regression  "));

        ArgumentCaptor<TestRun> savedRun = ArgumentCaptor.forClass(TestRun.class);
        verify(runRepository).save(savedRun.capture());
        assertThat(savedRun.getValue().getName()).isEqualTo("Regression");
        assertThat(savedRun.getValue().getTriggeredBy()).isSameAs(tester);
        assertThat(savedRun.getValue().getStatus()).isEqualTo(RunStatus.IN_PROGRESS);

        verify(resultRepository).saveAll(resultsCaptor.capture());
        assertThat(resultsCaptor.getValue())
                .hasSize(3)
                .allSatisfy(r -> {
                    assertThat(r.getStatus()).isEqualTo(ResultStatus.NOT_RUN);
                    assertThat(r.getRun()).isSameAs(savedRun.getValue());
                })
                .extracting(r -> r.getTestCase().getId())
                .containsExactly(1L, 2L, 3L);

        assertThat(response.name()).isEqualTo("Regression");
        assertThat(response.summary().total()).isEqualTo(3);
        assertThat(response.summary().notRun()).isEqualTo(3);
        assertThat(response.summary().passRate()).isZero();
    }

    @Test
    void create_rejectsASuiteWithNoActiveCases() {
        when(suiteService.getOwnedSuite(3L)).thenReturn(suite);
        when(testCaseRepository.findBySuiteIdAndStatusNotOrderByIdAsc(3L, TestCaseStatus.DEPRECATED)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new CreateRunRequest(3L, "Empty")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no active test cases");
        verify(runRepository, never()).save(any(TestRun.class));
        verifyNoInteractions(resultRepository);
    }

    @Test
    void create_createsNothingForASuiteTheUserDoesNotOwn() {
        doThrow(new AccessDeniedException("no access")).when(suiteService).getOwnedSuite(9L);

        assertThatThrownBy(() -> service.create(new CreateRunRequest(9L, "Sneaky")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(runRepository, resultRepository, testCaseRepository);
    }

    // ---------- list ----------

    @Test
    void list_attachesPerRunStatusCountsWithoutLoadingResults() {
        Pageable pageable = PageRequest.of(0, 10);
        TestRun first = run(10L, suite, RunStatus.COMPLETED);
        TestRun second = run(11L, suite, RunStatus.IN_PROGRESS);
        when(runRepository.findByProjectId(1L, pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(resultRepository.countByRunIds(List.of(10L, 11L))).thenReturn(List.of(
                runCount(10L, ResultStatus.PASSED, 3),
                runCount(10L, ResultStatus.FAILED, 1),
                runCount(11L, ResultStatus.NOT_RUN, 2)));

        Page<RunResponse> page = service.list(1L, pageable);

        verify(projectService).getOwnedProject(1L);
        RunResponse completed = page.getContent().get(0);
        assertThat(completed.summary().total()).isEqualTo(4);
        assertThat(completed.summary().passRate()).isEqualTo(75.0);
        RunResponse pending = page.getContent().get(1);
        assertThat(pending.summary().notRun()).isEqualTo(2);
        assertThat(pending.summary().passRate()).isZero();
    }

    @Test
    void list_skipsTheCountQueryWhenThereAreNoRuns() {
        Pageable pageable = PageRequest.of(0, 10);
        when(runRepository.findByProjectId(1L, pageable)).thenReturn(Page.empty(pageable));

        assertThat(service.list(1L, pageable).getContent()).isEmpty();
        verifyNoInteractions(resultRepository);
    }

    @Test
    void list_deniesAccessToAnotherUsersProject() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.list(2L, PageRequest.of(0, 10))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(runRepository);
    }

    // ---------- get ----------

    @Test
    void get_returnsTheRunWithItsResultsAndSummary() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestCase login = testCase(1L, suite, "Login", Priority.HIGH);
        TestCase logout = testCase(2L, suite, "Logout", Priority.LOW);
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(existing));
        when(resultRepository.findByRunIdOrderByIdAsc(10L)).thenReturn(List.of(
                result(100L, existing, login, ResultStatus.PASSED),
                result(101L, existing, logout, ResultStatus.NOT_RUN)));

        RunDetailResponse detail = service.get(10L);

        assertThat(detail.run().summary().total()).isEqualTo(2);
        assertThat(detail.run().summary().passed()).isEqualTo(1);
        assertThat(detail.run().summary().passRate()).isEqualTo(100.0);
        assertThat(detail.results()).extracting(RunResultResponse::title).containsExactly("Login", "Logout");
    }

    @Test
    void get_throwsNotFoundForAnUnknownRun() {
        when(runRepository.findWithDetailsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_deniesAccessToAnotherUsersRun() {
        TestRun existing = run(10L, suite, RunStatus.COMPLETED);
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.get(10L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(resultRepository);
    }

    // ---------- updateResult ----------

    @Test
    void updateResult_recordsTheOutcomeAndWhoExecutedIt() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.NOT_RUN);
        target.setDurationMs(0L);
        stubRunAndResult(existing, target);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(true);

        RunResultResponse response = service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.FAILED, "broken", 1500L));

        assertThat(target.getStatus()).isEqualTo(ResultStatus.FAILED);
        assertThat(target.getComment()).isEqualTo("broken");
        assertThat(target.getDurationMs()).isEqualTo(1500L);
        assertThat(target.getExecutedBy()).isSameAs(tester);
        assertThat(response.executedBy()).isEqualTo("manisha");
    }

    @Test
    void updateResult_keepsTheOldDurationWhenNoneIsSent() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.NOT_RUN);
        target.setDurationMs(900L);
        stubRunAndResult(existing, target);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(true);

        service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.PASSED, null, null));

        assertThat(target.getDurationMs()).isEqualTo(900L);
    }

    @Test
    void updateResult_resettingToNotRunClearsTheExecutor() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.PASSED);
        target.setExecutedBy(tester);
        stubRunAndResult(existing, target);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(true);

        service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.NOT_RUN, null, null));

        assertThat(target.getExecutedBy()).isNull();
        verifyNoInteractions(currentUserService);
    }

    @Test
    void updateResult_completesTheRunWhenNothingIsLeftToExecute() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.NOT_RUN);
        stubRunAndResult(existing, target);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(false);

        service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.PASSED, null, null));

        assertThat(existing.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(existing.getFinishedAt()).isNotNull();
    }

    @Test
    void updateResult_reopensACompletedRunWhenAResultIsResetToNotRun() {
        TestRun existing = run(10L, suite, RunStatus.COMPLETED);
        existing.setFinishedAt(LocalDateTime.now());
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.PASSED);
        stubRunAndResult(existing, target);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(true);

        service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.NOT_RUN, null, null));

        assertThat(existing.getStatus()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(existing.getFinishedAt()).isNull();
    }

    @Test
    void updateResult_leavesARunInProgressWhileResultsAreStillPending() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        TestRunResult target = result(100L, existing, testCase(1L, suite, "Login", Priority.HIGH), ResultStatus.NOT_RUN);
        stubRunAndResult(existing, target);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(resultRepository.existsByRunIdAndStatus(10L, ResultStatus.NOT_RUN)).thenReturn(true);

        service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.PASSED, null, null));

        assertThat(existing.getStatus()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(existing.getFinishedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"QUEUED", "RUNNING", "ABORTED"})
    void updateResult_isRefusedWhileARunIsAbortedOrStillRunning(RunStatus status) {
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(run(10L, suite, status)));

        assertThatThrownBy(() -> service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.PASSED, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aborted or still-running");
        verifyNoInteractions(resultRepository);
    }

    @Test
    void updateResult_throwsNotFoundWhenTheResultBelongsToAnotherRun() {
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(run(10L, suite, RunStatus.IN_PROGRESS)));
        when(resultRepository.findByIdAndRunId(555L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateResult(10L, 555L, new UpdateResultRequest(ResultStatus.PASSED, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("555");
    }

    @Test
    void updateResult_deniesAccessToAnotherUsersRun() {
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(run(10L, suite, RunStatus.IN_PROGRESS)));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.updateResult(10L, 100L, new UpdateResultRequest(ResultStatus.PASSED, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(resultRepository);
    }

    // ---------- abort ----------

    @Test
    void abort_marksAnInProgressRunAsAborted() {
        TestRun existing = run(10L, suite, RunStatus.IN_PROGRESS);
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(existing));
        when(resultRepository.countByRunId(10L)).thenReturn(List.of(
                statusCount(ResultStatus.PASSED, 2), statusCount(ResultStatus.NOT_RUN, 1)));

        RunResponse response = service.abort(10L);

        assertThat(existing.getStatus()).isEqualTo(RunStatus.ABORTED);
        assertThat(existing.getFinishedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(RunStatus.ABORTED);
        assertThat(response.summary().total()).isEqualTo(3);
        assertThat(response.summary().passRate()).isEqualTo(100.0);
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"QUEUED", "RUNNING", "COMPLETED", "FAILED", "ABORTED"})
    void abort_isRefusedUnlessTheRunIsInProgress(RunStatus status) {
        TestRun existing = run(10L, suite, status);
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.abort(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only a run that is in progress");
        assertThat(existing.getStatus()).isEqualTo(status);
    }

    @Test
    void abort_deniesAccessToAnotherUsersRun() {
        when(runRepository.findWithDetailsById(10L)).thenReturn(Optional.of(run(10L, suite, RunStatus.IN_PROGRESS)));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.abort(10L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(resultRepository);
    }

    // ---------- helpers ----------

    private void stubRunAndResult(TestRun existing, TestRunResult target) {
        when(runRepository.findWithDetailsById(existing.getId())).thenReturn(Optional.of(existing));
        when(resultRepository.findByIdAndRunId(target.getId(), existing.getId())).thenReturn(Optional.of(target));
    }

    private static TestRunResultRepository.RunStatusCount runCount(Long runId, ResultStatus status, long total) {
        return new TestRunResultRepository.RunStatusCount() {
            @Override public Long getRunId() { return runId; }
            @Override public ResultStatus getStatus() { return status; }
            @Override public long getTotal() { return total; }
        };
    }

    private static TestRunResultRepository.StatusCount statusCount(ResultStatus status, long total) {
        return new TestRunResultRepository.StatusCount() {
            @Override public ResultStatus getStatus() { return status; }
            @Override public long getTotal() { return total; }
        };
    }
}