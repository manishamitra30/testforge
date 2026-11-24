package com.testforge.backend.websocket;

import com.testforge.backend.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RunAccessChecker {

    private final TestRunRepository runRepository;

    // The WebSocket thread has no SecurityContext, so the username is passed in explicitly.
    @Transactional(readOnly = true)
    public boolean canView(Long runId, String username) {
        return runRepository.findWithDetailsById(runId)
                .map(run -> username.equals(run.getProject().getOwner().getUsername()))
                .orElse(false);
    }
}