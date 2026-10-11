package com.testforge.backend.service;

import com.testforge.backend.dto.ProjectRequest;
import com.testforge.backend.dto.ProjectResponse;
import com.testforge.backend.exception.ResourceNotFoundException;
import com.testforge.backend.mapper.ProjectMapper;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper mapper;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Pageable pageable) {
        User user = currentUserService.getCurrentUser();
        return projectRepository.findByOwnerId(user.getId(), pageable).map(mapper::toResponse);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        User user = currentUserService.getCurrentUser();
        Project saved = projectRepository.save(mapper.toEntity(request, user));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return mapper.toResponse(getOwnedProject(id));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = getOwnedProject(id);
        mapper.apply(request, project);
        return mapper.toResponse(project);
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.delete(getOwnedProject(id));
    }

    public Project getOwnedProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));
        assertOwner(project);
        return project;
    }

    public void assertOwner(Project project) {
        User user = currentUserService.getCurrentUser();
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this project");
        }
    }
}