package com.testforge.backend.repository;

import com.testforge.backend.model.TestCase;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    Page<TestCase> findBySuiteId(Long suiteId, Pageable pageable);

    @EntityGraph(attributePaths = "steps")
    Optional<TestCase> findWithStepsById(Long id);
}