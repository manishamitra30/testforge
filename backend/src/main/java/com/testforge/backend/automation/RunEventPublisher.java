package com.testforge.backend.automation;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public void log(Long runId, long offset, String line) {
        send(new RunEvent("LOG", runId, null, line, offset, Instant.now().toString()));
    }

    public void status(Long runId, String status) {
        send(new RunEvent("STATUS", runId, status, null, null, Instant.now().toString()));
    }

    // Streaming is best effort: a failure here must never break or fail a test run.
    private void send(RunEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/runs/" + event.runId(), mapper.writeValueAsString(event));
        } catch (RuntimeException ex) {
            log.debug("Could not publish {} event for run {}: {}", event.type(), event.runId(), ex.getMessage());
        }
    }
}