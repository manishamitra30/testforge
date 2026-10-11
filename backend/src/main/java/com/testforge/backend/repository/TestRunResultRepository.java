package com.testforge.backend.repository;

import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.TestRunResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRunResultRepository extends JpaRepository<TestRunResult, Long> {

    interface StatusCount {
        ResultStatus getStatus();

        long getTotal();
    }

    interface RunStatusCount {
        Long getRunId();

        ResultStatus getStatus();

        long getTotal();
    }

    @EntityGraph(attributePaths = {"testCase", "executedBy"})
    List<TestRunResult> findByRunIdOrderByIdAsc(Long runId);

    Optional<TestRunResult> findByIdAndRunId(Long id, Long runId);

    boolean existsByRunIdAndStatus(Long runId, ResultStatus status);

    @Query("select r.status as status, count(r) as total from TestRunResult r "
            + "where r.run.id = :runId group by r.status")
    List<StatusCount> countByRunId(@Param("runId") Long runId);

    @Query("select r.run.id as runId, r.status as status, count(r) as total from TestRunResult r "
            + "where r.run.id in :runIds group by r.run.id, r.status")
    List<RunStatusCount> countByRunIds(@Param("runIds") Collection<Long> runIds);
}