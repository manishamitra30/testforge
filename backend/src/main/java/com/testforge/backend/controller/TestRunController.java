package com.testforge.backend.controller;

import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.PageResponse;
import com.testforge.backend.dto.RunDetailResponse;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunResultResponse;
import com.testforge.backend.dto.UpdateResultRequest;
import com.testforge.backend.service.TestRunService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class TestRunController {

    private final TestRunService runService;

    @PostMapping
    public ResponseEntity<RunResponse> create(@Valid @RequestBody CreateRunRequest request) {
        RunResponse created = runService.create(request);
        return ResponseEntity.created(URI.create("/api/runs/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<RunResponse> list(
            @RequestParam Long projectId,
            @PageableDefault(size = 10, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(runService.list(projectId, pageable));
    }

    @GetMapping("/{id}")
    public RunDetailResponse get(@PathVariable Long id) {
        return runService.get(id);
    }

    @PatchMapping("/{runId}/results/{resultId}")
    public RunResultResponse updateResult(
            @PathVariable Long runId,
            @PathVariable Long resultId,
            @Valid @RequestBody UpdateResultRequest request) {
        return runService.updateResult(runId, resultId, request);
    }

    @PostMapping("/{id}/abort")
    public RunResponse abort(@PathVariable Long id) {
        return runService.abort(id);
    }
}