ALTER TABLE members ADD COLUMN IF NOT EXISTS email VARCHAR(254);

CREATE UNIQUE INDEX IF NOT EXISTS members_email_unique
  ON members (LOWER(email))
  WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS pending_signups (
  id BIGSERIAL PRIMARY KEY,
  member_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(254) NOT NULL,
  password_hash TEXT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS pending_signups_email_unique
  ON pending_signups (LOWER(email));
CREATE INDEX IF NOT EXISTS pending_signups_expiry_idx
  ON pending_signups (expires_at);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  token_hash CHAR(64) PRIMARY KEY,
  member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS password_reset_tokens_member_idx
  ON password_reset_tokens (member_id, created_at DESC);
CREATE INDEX IF NOT EXISTS password_reset_tokens_expiry_idx
  ON password_reset_tokens (expires_at);
