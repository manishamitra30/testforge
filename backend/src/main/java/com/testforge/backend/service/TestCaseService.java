package com.testforge.backend.service;

import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.mapper.TestCaseMapper;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.specification.TestCaseSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseMapper mapper;
    private final TestSuiteService suiteService;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    @Transactional
    public TestCaseResponse create(Long suiteId, TestCaseRequest request) {
        TestSuite suite = suiteService.getOwnedSuite(suiteId);
        User user = currentUserService.getCurrentUser();
        TestCase saved = testCaseRepository.save(mapper.toEntity(request, suite, user));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TestCaseSummaryResponse> search(
            Long projectId, Priority priority, TestCaseStatus status, String q, Pageable pageable) {
        projectService.getOwnedProject(projectId);
        return testCaseRepository
                .findAll(TestCaseSpecifications.filter(projectId, priority, status, q), pageable)
                .map(mapper::toSummary);
    }
}