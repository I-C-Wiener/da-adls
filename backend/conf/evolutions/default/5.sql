# --- !Ups

CREATE TABLE user_presence (
  user_id    BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  status     VARCHAR(10)  NOT NULL DEFAULT 'offline',
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rooms_name_fts ON rooms
  USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

# --- !Downs

DROP TABLE IF EXISTS user_presence;
DROP INDEX IF EXISTS idx_rooms_name_fts;
