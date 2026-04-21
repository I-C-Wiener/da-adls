# --- !Ups

CREATE TYPE room_visibility AS ENUM ('public', 'private');
CREATE TYPE member_role     AS ENUM ('owner', 'admin', 'member');

CREATE TABLE rooms (
  id          BIGSERIAL        PRIMARY KEY,
  name        VARCHAR(100)     NOT NULL UNIQUE,
  description TEXT,
  visibility  room_visibility  NOT NULL DEFAULT 'public',
  owner_id    BIGINT           NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ
);

CREATE TABLE room_members (
  room_id   BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role      member_role  NOT NULL DEFAULT 'member',
  joined_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  PRIMARY KEY (room_id, user_id)
);

CREATE TABLE room_bans (
  room_id   BIGINT      NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  banned_by BIGINT      NOT NULL REFERENCES users(id),
  banned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (room_id, user_id)
);

CREATE TABLE room_invitations (
  id              BIGSERIAL    PRIMARY KEY,
  room_id         BIGINT       NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  invited_by      BIGINT       NOT NULL REFERENCES users(id),
  invited_user_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_room_members_user ON room_members(user_id);
CREATE INDEX idx_rooms_name        ON rooms(name);

# --- !Downs

DROP TABLE IF EXISTS room_invitations;
DROP TABLE IF EXISTS room_bans;
DROP TABLE IF EXISTS room_members;
DROP TABLE IF EXISTS rooms;
DROP TYPE IF EXISTS member_role;
DROP TYPE IF EXISTS room_visibility;
