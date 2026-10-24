ALTER TABLE users
  ADD COLUMN full_name text,
  ADD COLUMN username text,
  ADD COLUMN email_verified_at timestamptz,
  ALTER COLUMN email DROP NOT NULL,
  ALTER COLUMN password_hash DROP NOT NULL,
  ADD CONSTRAINT users_full_name_check CHECK (length(full_name) BETWEEN 1 AND 100),
  ADD CONSTRAINT users_username_check CHECK (username ~ '^[a-z0-9_]{3,20}$'),
  ADD CONSTRAINT users_email_length_check CHECK (length(email) BETWEEN 3 AND 254),
  ADD CONSTRAINT users_password_needs_email_check CHECK (password_hash IS NULL OR email IS NOT NULL);
CREATE UNIQUE INDEX users_username_uq ON users (username);

CREATE TABLE user_identities (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider text NOT NULL CHECK (provider IN ('google', 'line')),
  subject text NOT NULL CHECK (length(subject) BETWEEN 1 AND 255),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, subject),
  UNIQUE (user_id, provider)
);

CREATE TABLE email_codes (
  id uuid PRIMARY KEY,
  purpose text NOT NULL
    CHECK (purpose IN ('REGISTER', 'VERIFY_EXISTING', 'RESET_PASSWORD', 'CHANGE_EMAIL', 'REAUTHENTICATE')),
  email text,
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  full_name text,
  username text,
  password_hash text,
  previous_email text,
  code_hash bytea,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  sends integer NOT NULL DEFAULT 1 CHECK (sends >= 1),
  last_sent_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_codes_expires_idx ON email_codes (expires_at);
CREATE INDEX email_codes_user_idx ON email_codes (user_id);
