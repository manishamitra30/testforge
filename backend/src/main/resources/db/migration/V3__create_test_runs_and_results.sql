-- Test runs
CREATE TABLE test_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    suite_id BIGINT NOT NULL REFERENCES test_suites(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP,
    triggered_by BIGINT REFERENCES users(id)
);

-- Results: one row per test case per run
CREATE TABLE test_run_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
    test_case_id BIGINT NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
    duration_ms BIGINT DEFAULT 0,
    comment TEXT,
    error_message TEXT,
    executed_by BIGINT REFERENCES users(id),
    CONSTRAINT uq_run_case UNIQUE (run_id, test_case_id)
);

-- Dashboard and lookup indexes
CREATE INDEX idx_test_runs_project_started ON test_runs(project_id, started_at);
CREATE INDEX idx_test_runs_suite ON test_runs(suite_id);
CREATE INDEX idx_test_run_results_case ON test_run_results(test_case_id);