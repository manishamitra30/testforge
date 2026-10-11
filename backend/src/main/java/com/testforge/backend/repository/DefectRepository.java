package com.testforge.backend.repository;

import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefectRepository
        extends JpaRepository<Defect, Long>, JpaSpecificationExecutor<Defect> {

    @Override
    @EntityGraph(attributePaths = {"reporter", "assignee", "testResult", "testResult.run", "testResult.testCase"})
    Page<Defect> findAll(Specification<Defect> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project.owner", "reporter", "assignee", "testResult", "testResult.run", "testResult.testCase"})
    Optional<Defect> findWithDetailsById(Long id);

    boolean existsByTestResultIdAndStatusNot(Long testResultId, DefectStatus status);
}