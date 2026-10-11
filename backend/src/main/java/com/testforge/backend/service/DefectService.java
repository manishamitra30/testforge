package com.testforge.backend.service;

import com.testforge.backend.dto.CreateDefectRequest;
import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.dto.UpdateDefectRequest;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.mapper.DefectMapper;
import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.DefectRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import com.testforge.backend.repository.UserRepository;
import com.testforge.backend.specification.DefectSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final DefectRepository defectRepository;
    private final TestRunResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final DefectMapper mapper;

    @Transactional
    public DefectResponse create(CreateDefectRequest request) {
        TestRunResult result = resultRepository.findWithContextById(request.testResultId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Test result with ID " + request.testResultId() + " not found"));
        TestRun run = result.getRun();
        projectService.assertOwner(run.getProject());

        if (result.getStatus() != ResultStatus.FAILED) {
            throw new BadRequestException("A defect can only be logged against a failed test result");
        }
        if (defectRepository.existsByTestResultIdAndStatusNot(result.getId(), DefectStatus.CLOSED)) {
            throw new BadRequestException("An open defect already exists for this test result");
        }

        Defect defect = new Defect();
        defect.setProject(run.getProject());
        defect.setTestResult(result);
        defect.setReporter(currentUserService.getCurrentUser());
        defect.setTitle(StringUtils.hasText(request.title())
                ? request.title().trim()
                : defaultTitle(result));
        defect.setDescription(StringUtils.hasText(request.description())
                ? request.description().trim()
                : defaultDescription(result));
        defect.setSeverity(request.severity() != null
                ? request.severity()
                : defaultSeverity(result.getTestCase().getPriority()));
        defect.setAssignee(resolveAssignee(request.assigneeId()));

        return mapper.toResponse(defectRepository.save(defect));
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> list(
            Long projectId, DefectStatus status, Severity severity, Pageable pageable) {
        projectService.getOwnedProject(projectId);
        return defectRepository
                .findAll(DefectSpecifications.filter(projectId, status, severity), pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DefectResponse get(Long id) {
        return mapper.toResponse(getOwnedDefect(id));
    }

    @Transactional
    public DefectResponse update(Long id, UpdateDefectRequest request) {
        Defect defect = getOwnedDefect(id);
        if (defect.getStatus() == DefectStatus.CLOSED) {
            throw new BadRequestException("A closed defect cannot be edited. Reopen it first");
        }
        defect.setTitle(request.title().trim());
        defect.setDescription(request.description());
        defect.setSeverity(request.severity());
        defect.setAssignee(resolveAssignee(request.assigneeId()));
        return mapper.toResponse(defect);
    }

    @Transactional
    public DefectResponse changeStatus(Long id, DefectStatus target) {
        Defect defect = getOwnedDefect(id);
        DefectStatus current = defect.getStatus();

        if (current == target) {
            throw new BadRequestException("The defect is already " + current);
        }
        if (!current.canTransitionTo(target)) {
            String hint = current == DefectStatus.CLOSED ? " Reopen it first (set the status to OPEN)." : "";
            throw new BadRequestException("Cannot move a defect from " + current + " to " + target
                    + ". Allowed: " + current.allowedTransitions() + "." + hint);
        }
        defect.setStatus(target);
        return mapper.toResponse(defect);
    }

    private Defect getOwnedDefect(Long id) {
        Defect defect = defectRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Defect with ID " + id + " not found"));
        projectService.assertOwner(defect.getProject());
        return defect;
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + assigneeId + " not found"));
    }

    private String defaultTitle(TestRunResult result) {
        return "Failed: " + result.getTestCase().getTitle();
    }

    private String defaultDescription(TestRunResult result) {
        TestRun run = result.getRun();
        StringBuilder text = new StringBuilder();
        text.append("Test case: ").append(result.getTestCase().getTitle()).append('\n');
        text.append("Suite: ").append(run.getSuite().getName()).append('\n');
        text.append("Run: ").append(run.getName()).append(" (#").append(run.getId()).append(")\n");
        text.append("Priority: ").append(result.getTestCase().getPriority());
        if (StringUtils.hasText(result.getComment())) {
            text.append("\n\nTester comment:\n").append(result.getComment().trim());
        }
        if (StringUtils.hasText(result.getErrorMessage())) {
            text.append("\n\nError:\n").append(result.getErrorMessage().trim());
        }
        return text.toString();
    }

    private Severity defaultSeverity(Priority priority) {
        return switch (priority) {
            case LOW -> Severity.LOW;
            case MEDIUM -> Severity.MEDIUM;
            case HIGH -> Severity.HIGH;
            case CRITICAL -> Severity.CRITICAL;
        };
    }
}