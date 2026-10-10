-- Additive security foundation. Apply before deploying this release.
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_state VARCHAR(32);
UPDATE users SET account_state = CASE WHEN email_verified THEN 'ACTIVE' ELSE 'PENDING_VERIFICATION' END
WHERE account_state IS NULL;
ALTER TABLE users ALTER COLUMN account_state SET DEFAULT 'PENDING_VERIFICATION';
ALTER TABLE users ALTER COLUMN account_state SET NOT NULL;
CREATE INDEX IF NOT EXISTS users_account_state_idx ON users(account_state);
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_sent_at TIMESTAMP;
ALTER TABLE users ALTER COLUMN verification_code TYPE VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS target VARCHAR(200);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS request_id VARCHAR(80);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS source_ip VARCHAR(45);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(300);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS metadata TEXT;

CREATE TABLE IF NOT EXISTS auth_sessions (
  id UUID PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  csrf_hash VARCHAR(64) NOT NULL,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMP NOT NULL,
  last_seen_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  ip_address VARCHAR(45),
  user_agent VARCHAR(500),
  device_label VARCHAR(120),
  mfa_complete BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS auth_sessions_user_idx ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS auth_sessions_expiry_idx ON auth_sessions(expires_at);

CREATE TABLE IF NOT EXISTS invitations (
  id UUID PRIMARY KEY, email VARCHAR(200) NOT NULL, intended_role VARCHAR(32) NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE, inviter_user_id INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL,
  accepted_at TIMESTAMP NULL, revoked_at TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS invitations_email_idx ON invitations(email);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id UUID PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE, created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS mfa_secrets (
  user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  encrypted_secret TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT FALSE,
  last_used_step BIGINT, created_at TIMESTAMP NOT NULL, confirmed_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
  id UUID PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash VARCHAR(100) NOT NULL, used_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS reauth_grants (
  id UUID PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE,
  session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  scope VARCHAR(80) NOT NULL, created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS reauth_grants_session_scope_idx ON reauth_grants(session_id, scope);

CREATE TABLE IF NOT EXISTS security_events (
  id BIGSERIAL PRIMARY KEY,
  actor_user_id INTEGER,
  target VARCHAR(200), action VARCHAR(100) NOT NULL, result VARCHAR(20) NOT NULL,
  request_id VARCHAR(80), source_ip VARCHAR(45), user_agent VARCHAR(300),
  metadata TEXT, created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS security_events_created_idx ON security_events(created_at);
