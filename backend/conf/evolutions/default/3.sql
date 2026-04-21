# --- !Ups

CREATE TABLE conversations (
  id                BIGSERIAL    PRIMARY KEY,
  room_id           BIGINT       REFERENCES rooms(id) ON DELETE CASCADE,
  conversation_type VARCHAR(10)  NOT NULL,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Each room gets one conversation row (created by room creation trigger or app logic)
CREATE UNIQUE INDEX idx_conversations_room ON conversations(room_id) WHERE room_id IS NOT NULL;

CREATE TABLE messages (
  id              BIGSERIAL    PRIMARY KEY,
  conversation_id BIGINT       NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id       BIGINT       NOT NULL REFERENCES users(id),
  content         VARCHAR(3072),
  reply_to_id     BIGINT       REFERENCES messages(id) ON DELETE SET NULL,
  is_edited       BOOLEAN      NOT NULL DEFAULT FALSE,
  edited_at       TIMESTAMPTZ,
  is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_expires_at           ON messages(expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE attachments (
  id                BIGSERIAL    PRIMARY KEY,
  message_id        BIGINT       NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  original_name     VARCHAR(255) NOT NULL,
  stored_path       VARCHAR(500) NOT NULL,
  mime_type         VARCHAR(100),
  file_size_bytes   BIGINT       NOT NULL,
  comment           TEXT,
  uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE unread_counts (
  user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  conversation_id      BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  count                INT    NOT NULL DEFAULT 0,
  last_read_message_id BIGINT REFERENCES messages(id),
  PRIMARY KEY (user_id, conversation_id)
);

# --- !Downs

DROP TABLE IF EXISTS unread_counts;
DROP TABLE IF EXISTS attachments;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversations;
