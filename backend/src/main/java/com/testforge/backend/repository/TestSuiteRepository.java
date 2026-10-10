package com.testforge.backend.repository;

import com.testforge.backend.model.TestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    Page<TestSuite> findByProjectId(Long projectId, Pageable pageable);
}