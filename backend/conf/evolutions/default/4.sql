# --- !Ups

CREATE TYPE friendship_status AS ENUM ('pending', 'accepted', 'rejected');

CREATE TABLE friendships (
  id           BIGSERIAL          PRIMARY KEY,
  requester_id BIGINT             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  addressee_id BIGINT             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status       friendship_status  NOT NULL DEFAULT 'pending',
  message      VARCHAR(500),
  created_at   TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
  UNIQUE (requester_id, addressee_id)
);

CREATE TABLE user_bans (
  banner_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  banned_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  banned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (banner_id, banned_id)
);

CREATE TABLE dm_conversations (
  conversation_id BIGINT  NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_a_id       BIGINT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_b_id       BIGINT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  frozen          BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (conversation_id)
);

CREATE UNIQUE INDEX idx_dm_users ON dm_conversations(
  LEAST(user_a_id, user_b_id),
  GREATEST(user_a_id, user_b_id)
);

# --- !Downs

DROP TABLE IF EXISTS dm_conversations;
DROP TABLE IF EXISTS user_bans;
DROP TABLE IF EXISTS friendships;
DROP TYPE IF EXISTS friendship_status;
