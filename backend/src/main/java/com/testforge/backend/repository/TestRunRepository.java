package com.testforge.backend.repository;

import com.testforge.backend.model.TestRun;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    @EntityGraph(attributePaths = {"project", "suite", "triggeredBy"})
    Page<TestRun> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"project.owner", "suite", "triggeredBy"})
    Optional<TestRun> findWithDetailsById(Long id);
}