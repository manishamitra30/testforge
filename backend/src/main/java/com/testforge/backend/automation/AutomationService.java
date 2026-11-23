package com.testforge.backend.automation;

import com.testforge.backend.common.exception.ServiceBusyException;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AutomationService {

    private final AutomationRunRecorder recorder;
    private final AutomationRunner runner;
    private final RunLogStore logStore;

    // Not transactional on purpose: the run is committed before the async job starts.
    public RunResponse trigger(CreateRunRequest request) {
        RunResponse run = recorder.createQueuedRun(request);
        logStore.append(run.id(), "Run queued");
        try {
            runner.execute(run.id());
        } catch (TaskRejectedException ex) {
            recorder.markFailed(run.id(), "The automation queue is full");
            throw new ServiceBusyException("The automation queue is full. Try again in a few minutes");
        }
        return run;
    }

    public RunLogStore.Chunk log(Long runId, long from) {
        recorder.assertAccess(runId);
        return logStore.read(runId, from);
    }
}