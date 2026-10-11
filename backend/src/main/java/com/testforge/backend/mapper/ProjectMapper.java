package com.testforge.backend.mapper;

import com.testforge.backend.dto.ProjectRequest;
import com.testforge.backend.dto.ProjectResponse;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.User;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(ProjectRequest request, User owner) {
        Project project = new Project();
        project.setOwner(owner);
        apply(request, project);
        return project;
    }

    public void apply(ProjectRequest request, Project project) {
        project.setName(request.name());
        project.setDescription(request.description());
    }

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getCreatedAt());
    }
}