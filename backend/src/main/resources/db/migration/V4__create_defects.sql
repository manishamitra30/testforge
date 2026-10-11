-- Defects
CREATE TABLE defects (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_result_id BIGINT REFERENCES test_run_results(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assignee_id BIGINT REFERENCES users(id),
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Dashboard and lookup indexes
CREATE INDEX idx_defects_project_status ON defects(project_id, status);
CREATE INDEX idx_defects_test_result ON defects(test_result_id);
CREATE INDEX idx_defects_assignee ON defects(assignee_id);