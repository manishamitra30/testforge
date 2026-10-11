package com.testforge.backend.controller;

import com.testforge.backend.dto.PageResponse;
import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.service.TestCaseService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping("/suites/{suiteId}/cases")
    public ResponseEntity<TestCaseResponse> create(
            @PathVariable Long suiteId, @Valid @RequestBody TestCaseRequest request) {
        TestCaseResponse created = testCaseService.create(suiteId, request);
        return ResponseEntity.created(URI.create("/api/cases/" + created.id())).body(created);
    }

    @GetMapping("/cases")
    public PageResponse<TestCaseSummaryResponse> search(
            @RequestParam Long projectId,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) TestCaseStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(testCaseService.search(projectId, priority, status, q, pageable));
    }
}