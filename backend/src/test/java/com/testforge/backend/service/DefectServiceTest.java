package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.defect;
import static com.testforge.backend.support.TestData.project;
import static com.testforge.backend.support.TestData.result;
import static com.testforge.backend.support.TestData.run;
import static com.testforge.backend.support.TestData.suite;
import static com.testforge.backend.support.TestData.testCase;
import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.dto.CreateDefectRequest;
import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.dto.UpdateDefectRequest;
import com.testforge.backend.mapper.DefectMapper;
import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.DefectRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import com.testforge.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DefectServiceTest {

    @Mock DefectRepository defectRepository;
    @Mock TestRunResultRepository resultRepository;
    @Mock UserRepository userRepository;
    @Mock ProjectService projectService;
    @Mock CurrentUserService currentUserService;
    @Spy DefectMapper mapper = new DefectMapper();
    @InjectMocks DefectService service;

    private final User tester = user(1L, "manisha");
    private final User colleague = user(5L, "dev-sam");
    private final Project project = project(1L, tester);
    private final TestSuite suite = suite(3L, project);
    private final TestRun run = run(7L, suite, RunStatus.COMPLETED);

    private TestRunResult failedResult(Priority priority) {
        run.setName("Smoke");
        suite.setName("Login suite");
        TestRunResult failed = result(100L, run, testCase(2L, suite, "Login with valid credentials", priority), ResultStatus.FAILED);
        failed.setErrorMessage("  expected 200 but was 500  ");
        return failed;
    }

    // ---------- create ----------

    @Test
    void create_buildsADefaultTitleDescriptionAndSeverityFromTheFailedResult() {
        TestRunResult failed = failedResult(Priority.HIGH);
        failed.setComment("Happened twice");
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failed));
        when(defectRepository.existsByTestResultIdAndStatusNot(100L, DefectStatus.CLOSED)).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(defectRepository.save(any(Defect.class))).thenAnswer(call -> call.getArgument(0));

        DefectResponse response = service.create(new CreateDefectRequest(100L, null, null, null, null));

        assertThat(response.title()).isEqualTo("Failed: Login with valid credentials");
        assertThat(response.severity()).isEqualTo(Severity.HIGH);
        assertThat(response.status()).isEqualTo(DefectStatus.OPEN);
        assertThat(response.reporterName()).isEqualTo("manisha");
        assertThat(response.assigneeId()).isNull();
        assertThat(response.description())
                .contains("Test case: Login with valid credentials")
                .contains("Suite: Login suite")
                .contains("Run: Smoke (#7)")
                .contains("Priority: HIGH")
                .contains("Tester comment:\nHappened twice")
                .contains("Error:\nexpected 200 but was 500");
    }

    @Test
    void create_usesTheValuesTheUserProvidedAndResolvesTheAssignee() {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(Priority.CRITICAL)));
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(userRepository.findById(5L)).thenReturn(Optional.of(colleague));
        when(defectRepository.save(any(Defect.class))).thenAnswer(call -> call.getArgument(0));

        DefectResponse response = service.create(
                new CreateDefectRequest(100L, "  Server error on login  ", "Steps to reproduce", Severity.LOW, 5L));

        assertThat(response.title()).isEqualTo("Server error on login");
        assertThat(response.description()).isEqualTo("Steps to reproduce");
        assertThat(response.severity()).isEqualTo(Severity.LOW);
        assertThat(response.assigneeId()).isEqualTo(5L);
    }

    @Test
    void create_treatsABlankTitleAsMissing() {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(Priority.MEDIUM)));
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(defectRepository.save(any(Defect.class))).thenAnswer(call -> call.getArgument(0));

        DefectResponse response = service.create(new CreateDefectRequest(100L, "   ", null, null, null));

        assertThat(response.title()).startsWith("Failed: ");
    }

    @Test
    void create_throwsNotFoundForAnUnknownResult() {
        when(resultRepository.findWithContextById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateDefectRequest(404L, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void create_deniesAccessWhenTheRunBelongsToAnotherUsersProject() {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(Priority.HIGH)));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.create(new CreateDefectRequest(100L, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @ParameterizedTest
    @CsvSource({"PASSED", "NOT_RUN", "BLOCKED", "SKIPPED"})
    void create_onlyAllowsDefectsAgainstFailedResults(ResultStatus status) {
        TestRunResult notFailed = failedResult(Priority.HIGH);
        notFailed.setStatus(status);
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(notFailed));

        assertThatThrownBy(() -> service.create(new CreateDefectRequest(100L, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("failed test result");
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void create_refusesADuplicateWhileAnotherDefectIsNotClosed() {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(Priority.HIGH)));
        when(defectRepository.existsByTestResultIdAndStatusNot(100L, DefectStatus.CLOSED)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateDefectRequest(100L, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void create_failsWhenTheAssigneeDoesNotExist() {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(Priority.HIGH)));
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateDefectRequest(100L, null, null, null, 77L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @ParameterizedTest
    @CsvSource({"LOW,LOW", "MEDIUM,MEDIUM", "HIGH,HIGH", "CRITICAL,CRITICAL"})
    void create_mapsTestCasePriorityToDefectSeverity(Priority priority, Severity expected) {
        when(resultRepository.findWithContextById(100L)).thenReturn(Optional.of(failedResult(priority)));
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(defectRepository.save(any(Defect.class))).thenAnswer(call -> call.getArgument(0));

        service.create(new CreateDefectRequest(100L, null, null, null, null));

        ArgumentCaptor<Defect> saved = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepository).save(saved.capture());
        assertThat(saved.getValue().getSeverity()).isEqualTo(expected);
    }

    // ---------- status workflow ----------

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "OPEN,IN_PROGRESS", "OPEN,CLOSED",
            "IN_PROGRESS,OPEN", "IN_PROGRESS,RESOLVED",
            "RESOLVED,OPEN", "RESOLVED,CLOSED",
            "CLOSED,OPEN"})
    void changeStatus_allowsTheDocumentedTransitions(DefectStatus from, DefectStatus to) {
        Defect existing = defect(1L, project, tester, from);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));

        DefectResponse response = service.changeStatus(1L, to);

        assertThat(existing.getStatus()).isEqualTo(to);
        assertThat(response.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "OPEN,RESOLVED",
            "IN_PROGRESS,CLOSED",
            "RESOLVED,IN_PROGRESS",
            "CLOSED,IN_PROGRESS",
            "CLOSED,RESOLVED"})
    void changeStatus_rejectsMovesTheWorkflowDoesNotAllow(DefectStatus from, DefectStatus to) {
        Defect existing = defect(1L, project, tester, from);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.changeStatus(1L, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot move a defect from " + from + " to " + to);
        assertThat(existing.getStatus()).isEqualTo(from);
    }

    @Test
    void changeStatus_tellsTheUserToReopenAClosedDefect() {
        when(defectRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(defect(1L, project, tester, DefectStatus.CLOSED)));

        assertThatThrownBy(() -> service.changeStatus(1L, DefectStatus.RESOLVED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reopen it first");
    }

    @ParameterizedTest
    @CsvSource({"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"})
    void changeStatus_rejectsAMoveToTheCurrentStatus(DefectStatus status) {
        when(defectRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(defect(1L, project, tester, status)));

        assertThatThrownBy(() -> service.changeStatus(1L, status))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already " + status);
    }

    @Test
    void changeStatus_deniesAccessToAnotherUsersDefect() {
        Defect existing = defect(1L, project, tester, DefectStatus.OPEN);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.changeStatus(1L, DefectStatus.IN_PROGRESS))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(existing.getStatus()).isEqualTo(DefectStatus.OPEN);
    }

    // ---------- update / get / list ----------

    @Test
    void update_changesTheEditableFieldsAndTrimsTheTitle() {
        Defect existing = defect(1L, project, tester, DefectStatus.OPEN);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(5L)).thenReturn(Optional.of(colleague));

        DefectResponse response = service.update(1L,
                new UpdateDefectRequest("  Better title ", "More detail", Severity.CRITICAL, 5L));

        assertThat(existing.getTitle()).isEqualTo("Better title");
        assertThat(existing.getDescription()).isEqualTo("More detail");
        assertThat(existing.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(response.assigneeName()).isEqualTo("dev-sam");
    }

    @Test
    void update_canRemoveTheAssignee() {
        Defect existing = defect(1L, project, tester, DefectStatus.IN_PROGRESS);
        existing.setAssignee(colleague);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, new UpdateDefectRequest("Title", null, Severity.LOW, null));

        assertThat(existing.getAssignee()).isNull();
    }

    @Test
    void update_isRefusedForAClosedDefect() {
        Defect existing = defect(1L, project, tester, DefectStatus.CLOSED);
        when(defectRepository.findWithDetailsById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, new UpdateDefectRequest("New", null, Severity.LOW, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("closed defect cannot be edited");
        assertThat(existing.getTitle()).isEqualTo("Defect 1");
    }

    @Test
    void get_returnsAnOwnedDefectWithItsAllowedTransitions() {
        when(defectRepository.findWithDetailsById(1L))
                .thenReturn(Optional.of(defect(1L, project, tester, DefectStatus.OPEN)));

        DefectResponse response = service.get(1L);

        assertThat(response.allowedTransitions()).containsExactlyInAnyOrder(DefectStatus.IN_PROGRESS, DefectStatus.CLOSED);
    }

    @Test
    void get_throwsNotFoundForAnUnknownDefect() {
        when(defectRepository.findWithDetailsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_checksProjectOwnershipThenFiltersInTheDatabase() {
        Pageable pageable = PageRequest.of(0, 10);
        when(defectRepository.findAll(ArgumentMatchers.<Specification<Defect>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(defect(1L, project, tester, DefectStatus.OPEN)), pageable, 1));

        Page<DefectResponse> page = service.list(1L, DefectStatus.OPEN, Severity.HIGH, pageable);

        verify(projectService).getOwnedProject(1L);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void list_deniesAccessToAnotherUsersProject() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.list(2L, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        verify(defectRepository, never()).findAll(ArgumentMatchers.<Specification<Defect>>any(), any(Pageable.class));
    }
}