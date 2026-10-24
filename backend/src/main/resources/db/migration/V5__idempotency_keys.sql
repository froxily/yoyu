CREATE TABLE idempotency_keys (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  idempotency_key text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 255),
  request_hash bytea NOT NULL,
  response_status integer,
  response_body text,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX idempotency_keys_expires_idx ON idempotency_keys (expires_at);
