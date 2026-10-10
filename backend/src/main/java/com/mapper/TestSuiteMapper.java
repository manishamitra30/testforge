package com.testforge.backend.mapper;

import com.testforge.backend.dto.TestSuiteRequest;
import com.testforge.backend.dto.TestSuiteResponse;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.TestSuite;
import org.springframework.stereotype.Component;

@Component
public class TestSuiteMapper {

    public TestSuite toEntity(TestSuiteRequest request, Project project) {
        TestSuite suite = new TestSuite();
        suite.setProject(project);
        apply(request, suite);
        return suite;
    }

    public void apply(TestSuiteRequest request, TestSuite suite) {
        suite.setName(request.name());
        suite.setDescription(request.description());
    }

    public TestSuiteResponse toResponse(TestSuite suite) {
        return new TestSuiteResponse(
                suite.getId(),
                suite.getProject().getId(),
                suite.getName(),
                suite.getDescription());
    }
}