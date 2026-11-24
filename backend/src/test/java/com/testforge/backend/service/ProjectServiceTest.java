package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.project;
import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.dto.ProjectRequest;
import com.testforge.backend.dto.ProjectResponse;
import com.testforge.backend.mapper.ProjectMapper;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock CurrentUserService currentUserService;
    @Spy ProjectMapper mapper = new ProjectMapper();
    @InjectMocks ProjectService service;

    private final User owner = user(1L, "manisha");
    private final User stranger = user(2L, "someone-else");

    @Test
    void list_returnsOnlyTheCurrentUsersProjects() {
        Pageable pageable = PageRequest.of(0, 10);
        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(projectRepository.findByOwnerId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(project(10L, owner)), pageable, 1));

        Page<ProjectResponse> page = service.list(pageable);

        assertThat(page.getContent()).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(10L);
            assertThat(p.ownerId()).isEqualTo(1L);
        });
    }

    @Test
    void create_makesTheCurrentUserTheOwner() {
        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(projectRepository.save(any(Project.class))).thenAnswer(call -> call.getArgument(0));

        ProjectResponse response = service.create(new ProjectRequest("Checkout", "Payment flows"));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
        assertThat(response.name()).isEqualTo("Checkout");
        assertThat(response.description()).isEqualTo("Payment flows");
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void get_returnsTheProjectToItsOwner() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(10L, owner)));
        when(currentUserService.getCurrentUser()).thenReturn(owner);

        assertThat(service.get(10L).id()).isEqualTo(10L);
    }

    @Test
    void get_throwsNotFoundForAnUnknownId() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void get_deniesAccessToSomeoneElsesProject() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(10L, owner)));
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        assertThatThrownBy(() -> service.get(10L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_changesNameAndDescriptionOfAnOwnedProject() {
        Project existing = project(10L, owner);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(currentUserService.getCurrentUser()).thenReturn(owner);

        ProjectResponse response = service.update(10L, new ProjectRequest("Renamed", "New text"));

        assertThat(existing.getName()).isEqualTo("Renamed");
        assertThat(response.description()).isEqualTo("New text");
    }

    @Test
    void update_neverChangesSomeoneElsesProject() {
        Project existing = project(10L, owner);
        existing.setName("Original");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        assertThatThrownBy(() -> service.update(10L, new ProjectRequest("Hacked", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(existing.getName()).isEqualTo("Original");
    }

    @Test
    void delete_removesAnOwnedProject() {
        Project existing = project(10L, owner);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(currentUserService.getCurrentUser()).thenReturn(owner);

        service.delete(10L);

        verify(projectRepository).delete(existing);
    }

    @Test
    void delete_refusesSomeoneElsesProject() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(10L, owner)));
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        assertThatThrownBy(() -> service.delete(10L)).isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).delete(any(Project.class));
    }
}