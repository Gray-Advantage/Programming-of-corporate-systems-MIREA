CREATE TABLE IF NOT EXISTS user_account (
 id UUID PRIMARY KEY, login VARCHAR(100) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, registered_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS display_name VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE user_role (
 user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
 role VARCHAR(16) NOT NULL CHECK (role IN ('READER','EDITOR','ADMIN')),
 PRIMARY KEY(user_id, role)
);
INSERT INTO user_role SELECT id,'EDITOR' FROM user_account ON CONFLICT DO NOTHING;
CREATE TABLE refresh_session (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE auth_audit (
 id UUID PRIMARY KEY, user_id UUID, actor VARCHAR(100) NOT NULL,
 event_type VARCHAR(60) NOT NULL, entity_type VARCHAR(60) NOT NULL,
 entity_id UUID, occurred_at TIMESTAMPTZ NOT NULL, description VARCHAR(500) NOT NULL,
 correlation_id VARCHAR(64) NOT NULL
);
CREATE INDEX user_account_search ON user_account(lower(login));
CREATE INDEX auth_audit_time ON auth_audit(occurred_at DESC);
CREATE TABLE login_attempt (
 attempt_key VARCHAR(180) PRIMARY KEY, failures INTEGER NOT NULL, locked_until TIMESTAMPTZ NOT NULL
);
