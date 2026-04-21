# --- !Ups

CREATE INDEX IF NOT EXISTS idx_attachments_message_id ON attachments(message_id);

# --- !Downs

DROP INDEX IF EXISTS idx_attachments_message_id;
