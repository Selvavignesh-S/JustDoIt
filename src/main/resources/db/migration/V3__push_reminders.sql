-- V3: reminders pushed to the phone (decided 2026-10-25; the SRS had deferred push to version 2).

-- One row per phone. A phone that signs in to another account moves to that account (token is the key).
CREATE TABLE push_tokens (
    token VARCHAR(200) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);

-- The reminder stage a task was last pushed at (1 Medium due soon, 2 High, 3 Due tomorrow, 4 Overdue),
-- so each climb to a more urgent stage is pushed once. Kept apart from tasks so the job never bumps a task's
-- row version (that would make the user's next edit fail with "changed on another device").
CREATE TABLE reminder_pushes (
    task_id BIGINT PRIMARY KEY REFERENCES tasks(task_id) ON DELETE CASCADE,
    stage SMALLINT NOT NULL,
    pushed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
