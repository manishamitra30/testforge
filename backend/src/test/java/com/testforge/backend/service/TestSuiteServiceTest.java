package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.project;
import static com.testforge.backend.support.TestData.suite;
import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.dto.TestSuiteRequest;
import com.testforge.backend.dto.TestSuiteResponse;
import com.testforge.backend.mapper.TestSuiteMapper;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.TestSuiteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class TestSuiteServiceTest {

    @Mock TestSuiteRepository suiteRepository;
    @Mock ProjectService projectService;
    @Spy TestSuiteMapper mapper = new TestSuiteMapper();
    @InjectMocks TestSuiteService service;

    private final User owner = user(1L, "manisha");
    private final Project project = project(1L, owner);

    @Test
    void listByProject_checksOwnershipThenReturnsTheSuites() {
        Pageable pageable = PageRequest.of(0, 10);
        when(projectService.getOwnedProject(1L)).thenReturn(project);
        when(suiteRepository.findByProjectId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(suite(5L, project)), pageable, 1));

        Page<TestSuiteResponse> page = service.listByProject(1L, pageable);

        assertThat(page.getContent()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo(5L);
            assertThat(s.projectId()).isEqualTo(1L);
        });
    }

    @Test
    void listByProject_doesNotTouchTheRepositoryWhenAccessIsDenied() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.listByProject(2L, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(suiteRepository);
    }

    @Test
    void create_savesTheSuiteInsideTheOwnedProject() {
        when(projectService.getOwnedProject(1L)).thenReturn(project);
        when(suiteRepository.save(any(TestSuite.class))).thenAnswer(call -> call.getArgument(0));

        TestSuiteResponse response = service.create(1L, new TestSuiteRequest("Smoke", "Fast checks"));

        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Smoke");
        assertThat(response.description()).isEqualTo("Fast checks");
    }

    @Test
    void create_savesNothingWhenAccessIsDenied() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.create(2L, new TestSuiteRequest("Smoke", null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(suiteRepository);
    }

    @Test
    void getOwnedSuite_returnsTheSuiteAfterAnOwnershipCheck() {
        TestSuite existing = suite(5L, project);
        when(suiteRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(service.getOwnedSuite(5L)).isSameAs(existing);
        verify(projectService).assertOwner(project);
    }

    @Test
    void getOwnedSuite_throwsNotFoundForAnUnknownId() {
        when(suiteRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedSuite(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getOwnedSuite_propagatesAnOwnershipFailure() {
        when(suiteRepository.findById(5L)).thenReturn(Optional.of(suite(5L, project)));
        doThrow(new AccessDeniedException("no access")).when(projectService).assertOwner(project);

        assertThatThrownBy(() -> service.getOwnedSuite(5L)).isInstanceOf(AccessDeniedException.class);
    }
}