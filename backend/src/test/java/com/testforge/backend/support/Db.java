package com.testforge.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts rows with plain SQL so tests control every value (including timestamps) and exercise the real
 * mappings only when they read the data back. Each method returns the new row's id.
 */
public final class Db {

    private final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long user(String username) {
        return user(username, "not-a-real-hash");
    }

    public long user(String username, String passwordHash) {
        return insert("insert into users (username, email, password) values (?, ?, ?) returning id",
                username, username + "@example.com", passwordHash);
    }

    public long project(long ownerId, String name) {
        return insert("insert into projects (name, owner_id) values (?, ?) returning id", name, ownerId);
    }

    public long suite(long projectId, String name) {
        return insert("insert into test_suites (project_id, name) values (?, ?) returning id", projectId, name);
    }

    public long testCase(long suiteId, String title, String priority, String status, String tags) {
        return insert("insert into test_cases (suite_id, title, priority, status, tags) values (?, ?, ?, ?, ?) returning id",
                suiteId, title, priority, status, tags);
    }

    public long step(long testCaseId, int number, String action, String expected) {
        return insert("insert into test_steps (test_case_id, step_number, action, expected_result) values (?, ?, ?, ?) returning id",
                testCaseId, number, action, expected);
    }

    /** {@code daysAgo} places started_at at noon, that many days before today (database date). */
    public long run(long projectId, long suiteId, String name, String status, int daysAgo, Long triggeredBy) {
        return insert("insert into test_runs (project_id, suite_id, name, status, started_at, triggered_by) "
                        + "values (?, ?, ?, ?, (current_date - ?::int) + time '12:00', ?) returning id",
                projectId, suiteId, name, status, daysAgo, triggeredBy);
    }

    public long result(long runId, long testCaseId, String status) {
        return insert("insert into test_run_results (run_id, test_case_id, status) values (?, ?, ?) returning id",
                runId, testCaseId, status);
    }

    public long defect(long projectId, Long resultId, String title, String severity, String status, long reporterId) {
        return insert("insert into defects (project_id, test_result_id, title, severity, status, reporter_id) "
                        + "values (?, ?, ?, ?, ?, ?) returning id",
                projectId, resultId, title, severity, status, reporterId);
    }

    public long count(String table) {
        Long total = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return total == null ? 0 : total;
    }

    private long insert(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        if (id == null) {
            throw new IllegalStateException("Insert returned no id: " + sql);
        }
        return id;
    }
}
