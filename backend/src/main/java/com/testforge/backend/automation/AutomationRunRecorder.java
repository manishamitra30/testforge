package com.testforge.backend.automation;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.mapper.TestRunMapper;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.repository.TestRunRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import com.testforge.backend.service.CurrentUserService;
import com.testforge.backend.service.ProjectService;
import com.testforge.backend.service.TestSuiteService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutomationRunRecorder {

    public record RunPlan(Long runId, List<Long> caseIds) {
    }

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final TestRunRepository runRepository;
    private final TestRunResultRepository resultRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteService suiteService;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final TestRunMapper mapper;

    @Transactional
    public RunResponse createQueuedRun(CreateRunRequest request) {
        TestSuite suite = suiteService.getOwnedSuite(request.suiteId());
        List<TestCase> cases = testCaseRepository
                .findBySuiteIdAndStatusNotOrderByIdAsc(suite.getId(), TestCaseStatus.DEPRECATED);
        if (cases.isEmpty()) {
            throw new BadRequestException("The suite has no active test cases to run");
        }
        User user = currentUserService.getCurrentUser();

        TestRun run = new TestRun();
        run.setProject(suite.getProject());
        run.setSuite(suite);
        run.setName(request.name().trim());
        run.setType(RunType.AUTOMATED);
        run.setStatus(RunStatus.QUEUED);
        run.setTriggeredBy(user);
        runRepository.save(run);

        List<TestRunResult> results = cases.stream().map(testCase -> {
            TestRunResult result = new TestRunResult();
            result.setRun(run);
            result.setTestCase(testCase);
            return result;
        }).toList();
        resultRepository.saveAll(results);

        return mapper.toResponse(run, Map.of(ResultStatus.NOT_RUN, (long) cases.size()));
    }

    @Transactional(readOnly = true)
    public void assertAccess(Long runId) {
        TestRun run = runRepository.findWithDetailsById(runId)
                .orElseThrow(() -> ResourceNotFoundException.of("Test run", runId));
        projectService.assertOwner(run.getProject());
    }

    @Transactional
    public Optional<RunPlan> startRun(Long runId) {
        Optional<TestRun> found = runRepository.findById(runId);
        if (found.isEmpty() || found.get().getStatus() != RunStatus.QUEUED) {
            return Optional.empty();
        }
        TestRun run = found.get();
        run.setStatus(RunStatus.RUNNING);
        List<Long> caseIds = resultRepository.findByRunIdOrderByIdAsc(runId).stream()
                .map(result -> result.getTestCase().getId())
                .toList();
        return Optional.of(new RunPlan(runId, caseIds));
    }

    @Transactional
    public void completeWithReport(Long runId, Map<Long, CaseOutcome> outcomes) {
        TestRun run = runRepository.findById(runId)
                .orElseThrow(() -> ResourceNotFoundException.of("Test run", runId));
        User executor = run.getTriggeredBy();

        for (TestRunResult result : resultRepository.findByRunIdOrderByIdAsc(runId)) {
            CaseOutcome outcome = outcomes.get(result.getTestCase().getId());
            if (outcome == null) {
                result.setStatus(ResultStatus.SKIPPED);
                result.setComment("No automated scenario is tagged @TC-" + result.getTestCase().getId());
            } else {
                result.setStatus(outcome.status());
                result.setDurationMs(outcome.durationMs());
                result.setErrorMessage(outcome.errorMessage());
                result.setExecutedBy(executor);
            }
        }
        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(LocalDateTime.now());
    }

    @Transactional
    public void markFailed(Long runId, String reason) {
        runRepository.findById(runId).ifPresent(run -> failRun(run, reason));
    }

    @Transactional
    public int failInterruptedRuns() {
        List<TestRun> stuck = runRepository.findByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
        stuck.forEach(run -> failRun(run, "The server restarted before the run finished"));
        return stuck.size();
    }

    private void failRun(TestRun run, String reason) {
        String text = "Automation run failed: " + reason;
        String message = text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
        for (TestRunResult result : resultRepository.findByRunIdOrderByIdAsc(run.getId())) {
            if (result.getStatus() == ResultStatus.NOT_RUN) {
                result.setStatus(ResultStatus.BLOCKED);
                result.setErrorMessage(message);
            }
        }
        run.setStatus(RunStatus.FAILED);
        run.setFinishedAt(LocalDateTime.now());
    }
}