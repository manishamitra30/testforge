package com.testforge.backend.controller;

import com.testforge.backend.dto.PageResponse;
import com.testforge.backend.dto.TestSuiteRequest;
import com.testforge.backend.dto.TestSuiteResponse;
import com.testforge.backend.service.TestSuiteService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/suites")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService suiteService;

    @GetMapping
    public PageResponse<TestSuiteResponse> list(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(suiteService.listByProject(projectId, pageable));
    }

    @PostMapping
    public ResponseEntity<TestSuiteResponse> create(
            @PathVariable Long projectId, @Valid @RequestBody TestSuiteRequest request) {
        TestSuiteResponse created = suiteService.create(projectId, request);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/suites/" + created.id()))
                .body(created);
    }
}