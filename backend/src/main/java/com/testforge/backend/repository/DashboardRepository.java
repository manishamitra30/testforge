package com.testforge.backend.repository;

import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestRunResult;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregate queries: everything is computed in the database. */
public interface DashboardRepository extends Repository<TestRunResult, Long> {

    interface SeverityTotal {
        Severity getSeverity();

        long getTotal();
    }

    interface DayRow {
        String getDay();

        long getPassed();

        long getExecuted();
    }

    interface ActivityRow {
        String getType();

        long getRefId();

        String getTitle();

        String getDetail();

        String getAt();
    }

    interface FlakyRow {
        long getTestCaseId();

        String getTitle();

        long getExecutions();

        long getFailures();

        long getFlips();
    }

    @Query(value = "select count(*) from test_cases tc join test_suites s on s.id = tc.suite_id "
            + "where s.project_id = :projectId", nativeQuery = true)
    long countCases(@Param("projectId") Long projectId);

    @Query("select count(r) from TestRun r where r.project.id = :projectId")
    long countRuns(@Param("projectId") Long projectId);

    @Query("select count(x) from TestRunResult x where x.run.project.id = :projectId "
            + "and x.status = com.testforge.backend.model.ResultStatus.PASSED")
    long countPassed(@Param("projectId") Long projectId);

    @Query("select count(x) from TestRunResult x where x.run.project.id = :projectId "
            + "and x.status <> com.testforge.backend.model.ResultStatus.NOT_RUN")
    long countExecuted(@Param("projectId") Long projectId);

    @Query("select count(d) from Defect d where d.project.id = :projectId "
            + "and d.status in (com.testforge.backend.model.DefectStatus.OPEN, "
            + "com.testforge.backend.model.DefectStatus.IN_PROGRESS)")
    long countOpenDefects(@Param("projectId") Long projectId);

    @Query("select d.severity as severity, count(d) as total from Defect d "
            + "where d.project.id = :projectId group by d.severity")
    List<SeverityTotal> countDefectsBySeverity(@Param("projectId") Long projectId);

    @Query(value = "select to_char(tr.started_at, 'YYYY-MM-DD') as \"day\", "
            + "count(*) filter (where r.status = 'PASSED') as \"passed\", "
            + "count(*) filter (where r.status <> 'NOT_RUN') as \"executed\" "
            + "from test_run_results r join test_runs tr on tr.id = r.run_id "
            + "where tr.project_id = :projectId and tr.started_at >= :since "
            + "group by to_char(tr.started_at, 'YYYY-MM-DD') "
            + "having count(*) filter (where r.status <> 'NOT_RUN') > 0 "
            + "order by 1", nativeQuery = true)
    List<DayRow> passRateByDay(@Param("projectId") Long projectId, @Param("since") LocalDateTime since);

    @Query(value = "select * from ("
            + "select 'RUN' as \"type\", tr.id as \"refId\", tr.name as \"title\", "
            + "tr.status as \"detail\", "
            + "to_char(coalesce(tr.finished_at, tr.started_at), 'YYYY-MM-DD\"T\"HH24:MI:SS') as \"at\" "
            + "from test_runs tr where tr.project_id = :projectId "
            + "union all "
            + "select 'DEFECT', d.id, d.title, d.severity || ' / ' || d.status, "
            + "to_char(d.created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS') "
            + "from defects d where d.project_id = :projectId"
            + ") a order by \"at\" desc limit :limit", nativeQuery = true)
    List<ActivityRow> recentActivity(@Param("projectId") Long projectId, @Param("limit") int limit);

    /**
     * Looks at the last {@code runs} finished runs of the project and counts, per test case, how many
     * times its result flipped between PASSED and FAILED from one run to the next.
     */
    @Query(value = "with recent as ("
            + "  select id from test_runs where project_id = :projectId "
            + "  and status in ('COMPLETED', 'FAILED') order by started_at desc limit :runs), "
            + "seq as ("
            + "  select r.test_case_id, r.status, "
            + "         lag(r.status) over (partition by r.test_case_id order by r.run_id) as prev "
            + "  from test_run_results r join recent on recent.id = r.run_id "
            + "  where r.status in ('PASSED', 'FAILED')) "
            + "select s.test_case_id as \"testCaseId\", tc.title as \"title\", "
            + "count(*) as \"executions\", "
            + "count(*) filter (where s.status = 'FAILED') as \"failures\", "
            + "count(*) filter (where s.prev is not null and s.prev <> s.status) as \"flips\" "
            + "from seq s join test_cases tc on tc.id = s.test_case_id "
            + "group by s.test_case_id, tc.title "
            + "having count(*) filter (where s.prev is not null and s.prev <> s.status) > 0 "
            + "order by \"flips\" desc, \"failures\" desc limit 20", nativeQuery = true)
    List<FlakyRow> flakyTests(@Param("projectId") Long projectId, @Param("runs") int runs);
}