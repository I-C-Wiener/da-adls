# --- !Ups

CREATE TABLE users (
  id            BIGSERIAL     PRIMARY KEY,
  email         VARCHAR(255)  NOT NULL UNIQUE,
  username      VARCHAR(50)   NOT NULL UNIQUE,
  password_hash VARCHAR(72)   NOT NULL,
  display_name  VARCHAR(100),
  avatar_url    VARCHAR(500),
  is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE user_sessions (
  id          BIGSERIAL     PRIMARY KEY,
  user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64)   NOT NULL UNIQUE,
  user_agent  VARCHAR(500),
  ip_address  INET,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  last_seen   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE password_reset_tokens (
  id          BIGSERIAL     PRIMARY KEY,
  user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64)   NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ   NOT NULL,
  used        BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_sessions_user  ON user_sessions(user_id);

# --- !Downs

DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS users;
