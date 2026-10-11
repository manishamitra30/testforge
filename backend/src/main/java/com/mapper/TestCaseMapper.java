package com.testforge.backend.mapper;

import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.dto.TestStepDto;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestStep;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TestCaseMapper {

    public TestCase toEntity(TestCaseRequest request, TestSuite suite, User createdBy) {
        TestCase testCase = new TestCase();
        testCase.setSuite(suite);
        testCase.setCreatedBy(createdBy);
        apply(request, testCase);
        return testCase;
    }

    public void apply(TestCaseRequest request, TestCase testCase) {
        testCase.setTitle(request.title());
        testCase.setDescription(request.description());
        testCase.setPreconditions(request.preconditions());
        testCase.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        testCase.setStatus(request.status() != null ? request.status() : TestCaseStatus.DRAFT);
        testCase.setTags(request.tags());

        List<TestStep> steps = request.steps() == null
                ? List.of()
                : request.steps().stream().map(this::toStep).toList();
        testCase.replaceSteps(steps);
    }

    public TestCaseResponse toResponse(TestCase testCase) {
        List<TestStepDto> steps = testCase.getSteps().stream()
                .sorted(Comparator.comparing(TestStep::getStepNumber))
                .map(s -> new TestStepDto(s.getStepNumber(), s.getAction(), s.getExpectedResult()))
                .toList();

        return new TestCaseResponse(
                testCase.getId(),
                testCase.getSuite().getId(),
                testCase.getTitle(),
                testCase.getDescription(),
                testCase.getPreconditions(),
                testCase.getPriority(),
                testCase.getStatus(),
                testCase.getTags(),
                testCase.getCreatedBy() == null ? null : testCase.getCreatedBy().getId(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt(),
                steps);
    }

    public TestCaseSummaryResponse toSummary(TestCase testCase) {
        return new TestCaseSummaryResponse(
                testCase.getId(),
                testCase.getSuite().getId(),
                testCase.getTitle(),
                testCase.getPriority(),
                testCase.getStatus(),
                testCase.getTags(),
                testCase.getUpdatedAt());
    }

    private TestStep toStep(TestStepDto dto) {
        TestStep step = new TestStep();
        step.setStepNumber(dto.stepNumber());
        step.setAction(dto.action());
        step.setExpectedResult(dto.expectedResult());
        return step;
    }
}