# --- !Ups

ALTER TABLE user_sessions ALTER COLUMN ip_address TYPE TEXT USING ip_address::TEXT;

# --- !Downs

ALTER TABLE user_sessions ALTER COLUMN ip_address TYPE INET USING ip_address::INET;
