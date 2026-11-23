package com.testforge.backend.automation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationStartupRecovery {

    private final AutomationRunRecorder recorder;

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedRuns() {
        int count = recorder.failInterruptedRuns();
        if (count > 0) {
            log.warn("Marked {} interrupted automation run(s) as FAILED", count);
        }
    }
}