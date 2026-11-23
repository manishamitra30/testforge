package com.testforge.backend.controller;

import com.testforge.backend.automation.AutomationService;
import com.testforge.backend.automation.RunLogStore;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    @PostMapping("/automated")
    public ResponseEntity<RunResponse> trigger(@Valid @RequestBody CreateRunRequest request) {
        RunResponse run = automationService.trigger(request);
        return ResponseEntity.accepted().location(URI.create("/api/runs/" + run.id())).body(run);
    }

    @GetMapping("/{id}/log")
    public RunLogStore.Chunk log(@PathVariable Long id, @RequestParam(defaultValue = "0") long from) {
        return automationService.log(id, from);
    }
}