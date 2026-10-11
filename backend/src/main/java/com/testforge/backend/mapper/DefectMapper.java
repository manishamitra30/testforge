package com.testforge.backend.mapper;

import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.model.Defect;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.User;
import org.springframework.stereotype.Component;

@Component
public class DefectMapper {

    public DefectResponse toResponse(Defect defect) {
        TestRunResult result = defect.getTestResult();
        User assignee = defect.getAssignee();
        User reporter = defect.getReporter();

        return new DefectResponse(
                defect.getId(),
                defect.getProject().getId(),
                result == null ? null : result.getId(),
                result == null ? null : result.getRun().getId(),
                result == null ? null : result.getRun().getName(),
                result == null ? null : result.getTestCase().getId(),
                result == null ? null : result.getTestCase().getTitle(),
                defect.getTitle(),
                defect.getDescription(),
                defect.getSeverity(),
                defect.getStatus(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getUsername(),
                reporter.getId(),
                reporter.getUsername(),
                defect.getCreatedAt(),
                defect.getUpdatedAt(),
                defect.getStatus().allowedTransitions());
    }
}