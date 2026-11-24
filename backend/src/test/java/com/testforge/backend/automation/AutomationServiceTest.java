package com.testforge.backend.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.ServiceBusyException;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunSummary;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.AccessDeniedException;

/** The real runner starts Maven; here it is a mock, so these tests stay fast and need no browser or Java process. */
@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock AutomationRunRecorder recorder;
    @Mock AutomationRunner runner;
    @Mock RunLogStore logStore;
    @InjectMocks AutomationService service;

    private final CreateRunRequest request = new CreateRunRequest(3L, "Nightly");
    private final RunResponse queued = new RunResponse(
            5L, 1L, 3L, "Suite", "Nightly", RunType.AUTOMATED, RunStatus.QUEUED,
            null, null, "manisha", new RunSummary(6, 0, 0, 0, 0, 6, 0.0));

    @Test
    void trigger_recordsTheRunLogsItAndHandsItToTheRunnerInThatOrder() {
        when(recorder.createQueuedRun(request)).thenReturn(queued);

        RunResponse response = service.trigger(request);

        assertThat(response).isSameAs(queued);
        InOrder order = inOrder(recorder, logStore, runner);
        order.verify(recorder).createQueuedRun(request);
        order.verify(logStore).append(5L, "Run queued");
        order.verify(runner).execute(5L);
    }

    @Test
    void trigger_marksTheRunFailedAndReportsBusyWhenTheQueueIsFull() {
        when(recorder.createQueuedRun(request)).thenReturn(queued);
        doThrow(new TaskRejectedException("queue full")).when(runner).execute(5L);

        assertThatThrownBy(() -> service.trigger(request))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessageContaining("queue is full");
        verify(recorder).markFailed(5L, "The automation queue is full");
    }

    @Test
    void trigger_startsNothingWhenTheRunCannotBeCreated() {
        when(recorder.createQueuedRun(request)).thenThrow(new AccessDeniedException("no access"));

        assertThatThrownBy(() -> service.trigger(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(logStore, runner);
    }

    @Test
    void log_checksAccessThenReturnsTheRequestedChunk() {
        RunLogStore.Chunk chunk = new RunLogStore.Chunk(List.of("line one", "line two"), 5L);
        when(logStore.read(5L, 3L)).thenReturn(chunk);

        assertThat(service.log(5L, 3L)).isSameAs(chunk);
        InOrder order = inOrder(recorder, logStore);
        order.verify(recorder).assertAccess(5L);
        order.verify(logStore).read(5L, 3L);
    }

    @Test
    void log_revealsNothingWhenAccessIsDenied() {
        doThrow(new AccessDeniedException("no access")).when(recorder).assertAccess(5L);

        assertThatThrownBy(() -> service.log(5L, 0L)).isInstanceOf(AccessDeniedException.class);
        verify(logStore, never()).read(5L, 0L);
    }
}