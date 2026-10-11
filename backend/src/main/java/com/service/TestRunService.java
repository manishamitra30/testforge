package com.testforge.backend.service;

import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunDetailResponse;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunResultResponse;
import com.testforge.backend.dto.UpdateResultRequest;
import com.testforge.backend.exception.BadRequestException;
import com.testforge.backend.exception.ResourceNotFoundException;
import com.testforge.backend.mapper.TestRunMapper;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestRunService {

    private final TestRunRepository runRepository;
    private final TestRunResultRepository resultRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteService suiteService;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final TestRunMapper mapper;

    @Transactional
    public RunResponse create(CreateRunRequest request) {
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
    public Page<RunResponse> list(Long projectId, Pageable pageable) {
        projectService.getOwnedProject(projectId);
        Page<TestRun> page = runRepository.findByProjectId(projectId, pageable);

        List<Long> ids = page.getContent().stream().map(TestRun::getId).toList();
        Map<Long, Map<ResultStatus, Long>> countsByRun = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TestRunResultRepository.RunStatusCount row : resultRepository.countByRunIds(ids)) {
                countsByRun
                        .computeIfAbsent(row.getRunId(), key -> new EnumMap<>(ResultStatus.class))
                        .put(row.getStatus(), row.getTotal());
            }
        }
        return page.map(run -> mapper.toResponse(run, countsByRun.getOrDefault(run.getId(), Map.of())));
    }

    @Transactional(readOnly = true)
    public RunDetailResponse get(Long id) {
        TestRun run = getOwnedRun(id);
        List<TestRunResult> results = resultRepository.findByRunIdOrderByIdAsc(id);

        Map<ResultStatus, Long> counts = new EnumMap<>(ResultStatus.class);
        for (TestRunResult result : results) {
            counts.merge(result.getStatus(), 1L, Long::sum);
        }

        List<RunResultResponse> items = results.stream().map(mapper::toResultResponse).toList();
        return new RunDetailResponse(mapper.toResponse(run, counts), items);
    }

    @Transactional
    public RunResultResponse updateResult(Long runId, Long resultId, UpdateResultRequest request) {
        TestRun run = getOwnedRun(runId);
        if (run.getStatus() == RunStatus.ABORTED) {
            throw new BadRequestException("Results of an aborted run cannot be changed");
        }
        TestRunResult result = resultRepository.findByIdAndRunId(resultId, runId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Result " + resultId + " not found in run " + runId));

        result.setStatus(request.status());
        result.setComment(request.comment());
        if (request.durationMs() != null) {
            result.setDurationMs(request.durationMs());
        }
        result.setExecutedBy(request.status() == ResultStatus.NOT_RUN
                ? null
                : currentUserService.getCurrentUser());

        syncRunStatus(run);
        return mapper.toResultResponse(result);
    }

    @Transactional
    public RunResponse abort(Long id) {
        TestRun run = getOwnedRun(id);
        if (run.getStatus() != RunStatus.IN_PROGRESS) {
            throw new BadRequestException("Only a run that is in progress can be aborted");
        }
        run.setStatus(RunStatus.ABORTED);
        run.setFinishedAt(LocalDateTime.now());
        return mapper.toResponse(run, countsFor(id));
    }

    private void syncRunStatus(TestRun run) {
        boolean pending = resultRepository.existsByRunIdAndStatus(run.getId(), ResultStatus.NOT_RUN);
        if (!pending && run.getStatus() == RunStatus.IN_PROGRESS) {
            run.setStatus(RunStatus.COMPLETED);
            run.setFinishedAt(LocalDateTime.now());
        } else if (pending && run.getStatus() == RunStatus.COMPLETED) {
            run.setStatus(RunStatus.IN_PROGRESS);
            run.setFinishedAt(null);
        }
    }

    private Map<ResultStatus, Long> countsFor(Long runId) {
        Map<ResultStatus, Long> counts = new EnumMap<>(ResultStatus.class);
        for (TestRunResultRepository.StatusCount row : resultRepository.countByRunId(runId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    private TestRun getOwnedRun(Long id) {
        TestRun run = runRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test run " + id + " not found"));
        projectService.assertOwner(run.getProject());
        return run;
    }
}