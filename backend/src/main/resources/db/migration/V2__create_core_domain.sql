-- Projects
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Project members (access control)
CREATE TABLE project_members (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (project_id, user_id)
);

-- Test suites
CREATE TABLE test_suites (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT
);

-- Test cases
CREATE TABLE test_cases (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL REFERENCES test_suites(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    preconditions TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    tags VARCHAR(255),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Test steps
CREATE TABLE test_steps (
    id BIGSERIAL PRIMARY KEY,
    test_case_id BIGINT NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    action TEXT NOT NULL,
    expected_result TEXT NOT NULL
);

-- Indexes on foreign keys
CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_test_suites_project ON test_suites(project_id);
CREATE INDEX idx_test_cases_suite ON test_cases(suite_id);
CREATE INDEX idx_test_cases_created_by ON test_cases(created_by);
CREATE INDEX idx_test_steps_case ON test_steps(test_case_id);