package com.testforge.backend.controller;

import com.testforge.backend.dto.ChangeDefectStatusRequest;
import com.testforge.backend.dto.CreateDefectRequest;
import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.dto.PageResponse;
import com.testforge.backend.dto.UpdateDefectRequest;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Severity;
import com.testforge.backend.service.DefectService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @PostMapping
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody CreateDefectRequest request) {
        DefectResponse created = defectService.create(request);
        return ResponseEntity.created(URI.create("/api/defects/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<DefectResponse> list(
            @RequestParam Long projectId,
            @RequestParam(required = false) DefectStatus status,
            @RequestParam(required = false) Severity severity,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(defectService.list(projectId, status, severity, pageable));
    }

    @GetMapping("/{id}")
    public DefectResponse get(@PathVariable Long id) {
        return defectService.get(id);
    }

    @PutMapping("/{id}")
    public DefectResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDefectRequest request) {
        return defectService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DefectResponse changeStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeDefectStatusRequest request) {
        return defectService.changeStatus(id, request.status());
    }
}