package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.project;
import static com.testforge.backend.support.TestData.suite;
import static com.testforge.backend.support.TestData.testCase;
import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.dto.TestStepDto;
import com.testforge.backend.mapper.TestCaseMapper;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.TestCaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock TestCaseRepository testCaseRepository;
    @Mock TestSuiteService suiteService;
    @Mock ProjectService projectService;
    @Mock CurrentUserService currentUserService;
    @Spy TestCaseMapper mapper = new TestCaseMapper();
    @InjectMocks TestCaseService service;

    private final User tester = user(1L, "manisha");
    private final Project project = project(1L, tester);
    private final TestSuite suite = suite(5L, project);

    @Test
    void create_savesTheCaseWithItsStepsInTheOwnedSuite() {
        when(suiteService.getOwnedSuite(5L)).thenReturn(suite);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(call -> call.getArgument(0));

        TestCaseRequest request = new TestCaseRequest(
                "Login works", "desc", "a user exists", Priority.HIGH, TestCaseStatus.READY, "smoke",
                List.of(new TestStepDto(2, "Click login", "Dashboard opens"),
                        new TestStepDto(1, "Enter credentials", "Fields accept input")));

        TestCaseResponse response = service.create(5L, request);

        ArgumentCaptor<TestCase> saved = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(saved.capture());
        assertThat(saved.getValue().getSteps()).hasSize(2).allSatisfy(step ->
                assertThat(step.getTestCase()).isSameAs(saved.getValue()));
        assertThat(saved.getValue().getCreatedBy()).isSameAs(tester);

        assertThat(response.suiteId()).isEqualTo(5L);
        assertThat(response.createdBy()).isEqualTo(1L);
        assertThat(response.priority()).isEqualTo(Priority.HIGH);
        assertThat(response.steps()).extracting(TestStepDto::stepNumber).containsExactly(1, 2);
    }

    @Test
    void create_appliesDefaultsWhenOptionalFieldsAreMissing() {
        when(suiteService.getOwnedSuite(5L)).thenReturn(suite);
        when(currentUserService.getCurrentUser()).thenReturn(tester);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(call -> call.getArgument(0));

        TestCaseResponse response = service.create(5L,
                new TestCaseRequest("Minimal case", null, null, null, null, null, null));

        assertThat(response.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(response.status()).isEqualTo(TestCaseStatus.DRAFT);
        assertThat(response.steps()).isEmpty();
    }

    @Test
    void create_savesNothingWhenTheSuiteIsNotOwned() {
        doThrow(new AccessDeniedException("no access")).when(suiteService).getOwnedSuite(9L);

        assertThatThrownBy(() -> service.create(9L,
                new TestCaseRequest("Case", null, null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(testCaseRepository, never()).save(any(TestCase.class));
        verifyNoInteractions(currentUserService);
    }

    @Test
    void search_checksOwnershipAndMapsTheResultsToSummaries() {
        Pageable pageable = PageRequest.of(0, 10);
        TestCase found = testCase(7L, suite, "Login works", Priority.HIGH);
        when(testCaseRepository.findAll(ArgumentMatchers.<Specification<TestCase>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(found), pageable, 1));

        Page<TestCaseSummaryResponse> page = service.search(1L, Priority.HIGH, null, "login", pageable);

        verify(projectService).getOwnedProject(1L);
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(7L);
            assertThat(summary.suiteId()).isEqualTo(5L);
            assertThat(summary.title()).isEqualTo("Login works");
        });
    }

    @Test
    void search_doesNotQueryWhenTheProjectIsNotOwned() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.search(2L, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(testCaseRepository);
    }
}