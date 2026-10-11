package com.testforge.backend.repository;

import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestCaseRepository
        extends JpaRepository<TestCase, Long>, JpaSpecificationExecutor<TestCase> {

    Page<TestCase> findBySuiteId(Long suiteId, Pageable pageable);

    List<TestCase> findBySuiteIdAndStatusNotOrderByIdAsc(Long suiteId, TestCaseStatus status);

    @EntityGraph(attributePaths = "steps")
    Optional<TestCase> findWithStepsById(Long id);
}