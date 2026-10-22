-- V2: email verification codes (SRS 7.5 step 1) and case-insensitive usernames/emails

-- Same shape as password_reset_tokens: only the SHA-256 of the code is stored, single use, short expiry.
-- `email` records which address the code was sent to, so a code stops working if the user changes their email.
CREATE TABLE email_verification_tokens (
    token_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_hash ON email_verification_tokens(token_hash);

-- The backend lowercases usernames and emails before saving, so "Selva" and "selva" are one account.
-- These checks are the database backstop: with only lowercase values allowed, the existing UNIQUE
-- constraints become case-insensitive in effect.
ALTER TABLE users ADD CONSTRAINT chk_users_username_lowercase CHECK (username = LOWER(username));
ALTER TABLE users ADD CONSTRAINT chk_users_email_lowercase CHECK (email = LOWER(email));
