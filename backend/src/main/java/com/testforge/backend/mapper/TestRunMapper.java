package com.testforge.backend.mapper;

import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunResultResponse;
import com.testforge.backend.dto.RunSummary;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TestRunMapper {

    public RunResponse toResponse(TestRun run, Map<ResultStatus, Long> counts) {
        return new RunResponse(
                run.getId(),
                run.getProject().getId(),
                run.getSuite().getId(),
                run.getSuite().getName(),
                run.getName(),
                run.getType(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getTriggeredBy() == null ? null : run.getTriggeredBy().getUsername(),
                toSummary(counts));
    }

    public RunSummary toSummary(Map<ResultStatus, Long> counts) {
        long passed = counts.getOrDefault(ResultStatus.PASSED, 0L);
        long failed = counts.getOrDefault(ResultStatus.FAILED, 0L);
        long blocked = counts.getOrDefault(ResultStatus.BLOCKED, 0L);
        long skipped = counts.getOrDefault(ResultStatus.SKIPPED, 0L);
        long notRun = counts.getOrDefault(ResultStatus.NOT_RUN, 0L);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long executed = total - notRun;
        double passRate = executed == 0 ? 0.0 : Math.round(passed * 1000.0 / executed) / 10.0;
        return new RunSummary(total, passed, failed, blocked, skipped, notRun, passRate);
    }

    public RunResultResponse toResultResponse(TestRunResult result) {
        return new RunResultResponse(
                result.getId(),
                result.getTestCase().getId(),
                result.getTestCase().getTitle(),
                result.getTestCase().getPriority(),
                result.getStatus(),
                result.getDurationMs(),
                result.getComment(),
                result.getErrorMessage(),
                result.getExecutedBy() == null ? null : result.getExecutedBy().getUsername());
    }
}