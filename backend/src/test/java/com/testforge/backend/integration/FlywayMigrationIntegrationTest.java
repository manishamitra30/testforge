package com.testforge.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testforge.backend.support.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Checks the real schema produced by the Flyway migrations. (The application context only starts at all
 * because ddl-auto=validate agrees that every entity matches this schema.)
 */
class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Test
    void everyMigrationIsAppliedInOrderAndSucceeded() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    @Test
    void allDomainTablesExist() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "projects", "project_members", "test_suites", "test_cases", "test_steps",
                "test_runs", "test_run_results", "defects");
    }

    @Test
    void theIndexesTheQueriesDependOnExist() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "idx_projects_owner", "idx_test_suites_project", "idx_test_cases_suite", "idx_test_cases_created_by",
                "idx_test_steps_case", "idx_test_runs_project_started", "idx_test_runs_suite",
                "idx_test_run_results_case", "idx_defects_project_status", "idx_defects_test_result",
                "idx_defects_assignee");
    }

    @Test
    void aTestCaseCanAppearOnlyOncePerRun() {
        long owner = db.user("owner");
        long project = db.project(owner, "P");
        long suite = db.suite(project, "S");
        long testCase = db.testCase(suite, "Case", "MEDIUM", "READY", null);
        long run = db.run(project, suite, "Run", "IN_PROGRESS", 0, owner);
        db.result(run, testCase, "NOT_RUN");

        assertThatThrownBy(() -> db.result(run, testCase, "PASSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usernameAndEmailMustBeUnique() {
        db.user("manisha");

        assertThatThrownBy(() -> db.user("manisha")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (username, email, password) values ('other', 'manisha@example.com', 'x')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysRejectRowsThatPointNowhere() {
        assertThatThrownBy(() -> db.project(9999L, "Orphan")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiredColumnsCannotBeNull() {
        long owner = db.user("owner");

        assertThatThrownBy(() -> jdbc.update("insert into projects (name, owner_id) values (null, ?)", owner))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTestCaseDefaultsToMediumPriorityAndDraftStatus() {
        long owner = db.user("owner");
        long suite = db.suite(db.project(owner, "P"), "S");
        long id = jdbc.queryForObject("insert into test_cases (suite_id, title) values (?, 'Bare') returning id", Long.class, suite);

        Map<String, Object> row = jdbc.queryForMap("select priority, status from test_cases where id = ?", id);

        assertThat(row).containsEntry("priority", "MEDIUM").containsEntry("status", "DRAFT");
    }

    @Test
    void deletingAProjectCascadesThroughSuitesCasesStepsRunsAndResults() {
        long owner = db.user("owner");
        long project = db.project(owner, "P");
        long suite = db.suite(project, "S");
        long testCase = db.testCase(suite, "Case", "HIGH", "READY", null);
        db.step(testCase, 1, "do", "see");
        long run = db.run(project, suite, "Run", "COMPLETED", 1, owner);
        long result = db.result(run, testCase, "FAILED");
        db.defect(project, result, "Bug", "HIGH", "OPEN", owner);

        jdbc.update("delete from projects where id = ?", project);

        for (String table : List.of("test_suites", "test_cases", "test_steps", "test_runs", "test_run_results", "defects")) {
            assertThat(db.count(table)).as(table).isZero();
        }
        assertThat(db.count("users")).isEqualTo(1);
    }

    @Test
    void deletingAResultKeepsTheDefectButClearsItsLink() {
        long owner = db.user("owner");
        long project = db.project(owner, "P");
        long suite = db.suite(project, "S");
        long testCase = db.testCase(suite, "Case", "HIGH", "READY", null);
        long run = db.run(project, suite, "Run", "COMPLETED", 1, owner);
        long result = db.result(run, testCase, "FAILED");
        long defect = db.defect(project, result, "Bug", "HIGH", "OPEN", owner);

        jdbc.update("delete from test_run_results where id = ?", result);

        Long link = jdbc.queryForObject("select test_result_id from defects where id = ?", Long.class, defect);
        assertThat(db.count("defects")).isEqualTo(1);
        assertThat(link).isNull();
    }
}
