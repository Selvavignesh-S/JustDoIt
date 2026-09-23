-- V1__init_schema.sql: JustDoIt Core Schema Migration
-- Matches AI_Task_Manager_SRS_v1_4.md Section 18

-- 1. USER TABLE
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. TASK TABLE
CREATE TABLE tasks (
    task_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(20) NOT NULL CHECK (
        category IN (
            'ASSIGNMENT', 'PROJECT', 'EXAM', 'QUIZ', 'LAB',
            'PRESENTATION', 'MEETING', 'LEARNING', 'PERSONAL',
            'ADMINISTRATIVE', 'UNCATEGORIZED'
        )
    ),
    deadline TIMESTAMPTZ NOT NULL,
    estimated_hours NUMERIC(4, 1) NOT NULL CHECK (estimated_hours > 0),
    initial_priority VARCHAR(15) NOT NULL CHECK (
        initial_priority IN ('LOW', 'MEDIUM', 'HIGH', 'DUE_TOMORROW')
    ),
    current_priority VARCHAR(15) NOT NULL CHECK (
        current_priority IN ('LOW', 'MEDIUM', 'HIGH', 'DUE_TOMORROW')
    ),
    task_status VARCHAR(15) NOT NULL CHECK (
        task_status IN ('PENDING', 'COMPLETED')
    ),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. ATTACHMENT TABLE (circular reference resolved: current_version_id is nullable)
CREATE TABLE attachments (
    attachment_id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(task_id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    current_version_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. ATTACHMENT_VERSION TABLE
CREATE TABLE attachment_versions (
    version_id BIGSERIAL PRIMARY KEY,
    attachment_id BIGINT NOT NULL REFERENCES attachments(attachment_id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) UNIQUE NOT NULL,
    change_description TEXT,
    version INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attachment_version UNIQUE (attachment_id, version_number)
);

-- Add Foreign Key for attachments.current_version_id -> attachment_versions(version_id)
ALTER TABLE attachments
    ADD CONSTRAINT fk_attachments_current_version
    FOREIGN KEY (current_version_id)
    REFERENCES attachment_versions(version_id)
    ON DELETE SET NULL;

-- 5. PASSWORD_RESET_TOKEN TABLE
CREATE TABLE password_reset_tokens (
    token_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. INDEXES
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_current_priority ON tasks(current_priority);
CREATE INDEX idx_tasks_deadline ON tasks(deadline);
CREATE INDEX idx_tasks_status ON tasks(task_status);
CREATE INDEX idx_tasks_dashboard ON tasks(user_id, task_status, current_priority, deadline);

CREATE INDEX idx_attachments_task_id ON attachments(task_id);
CREATE INDEX idx_attachment_versions_attachment_id ON attachment_versions(attachment_id);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_hash ON password_reset_tokens(token_hash);
