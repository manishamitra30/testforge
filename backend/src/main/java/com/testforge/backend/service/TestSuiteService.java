package com.testforge.backend.service;

import com.testforge.backend.dto.TestSuiteRequest;
import com.testforge.backend.dto.TestSuiteResponse;
import com.testforge.backend.exception.ResourceNotFoundException;
import com.testforge.backend.mapper.TestSuiteMapper;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestSuiteService {

    private final TestSuiteRepository suiteRepository;
    private final TestSuiteMapper mapper;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public Page<TestSuiteResponse> listByProject(Long projectId, Pageable pageable) {
        projectService.getOwnedProject(projectId);
        return suiteRepository.findByProjectId(projectId, pageable).map(mapper::toResponse);
    }

    @Transactional
    public TestSuiteResponse create(Long projectId, TestSuiteRequest request) {
        Project project = projectService.getOwnedProject(projectId);
        TestSuite saved = suiteRepository.save(mapper.toEntity(request, project));
        return mapper.toResponse(saved);
    }

    public TestSuite getOwnedSuite(Long suiteId) {
        TestSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite " + suiteId + " not found"));
        projectService.assertOwner(suite.getProject());
        return suite;
    }
}