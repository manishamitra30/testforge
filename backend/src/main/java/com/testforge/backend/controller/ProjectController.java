package com.testforge.backend.controller;

import com.testforge.backend.dto.PageResponse;
import com.testforge.backend.dto.ProjectRequest;
import com.testforge.backend.dto.ProjectResponse;
import com.testforge.backend.service.ProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public PageResponse<ProjectResponse> list(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(projectService.list(pageable));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}