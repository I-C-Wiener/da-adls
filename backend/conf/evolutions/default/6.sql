# --- !Ups

ALTER TABLE rooms ALTER COLUMN visibility DROP DEFAULT;
ALTER TABLE room_members ALTER COLUMN role DROP DEFAULT;
ALTER TABLE friendships ALTER COLUMN status DROP DEFAULT;

ALTER TABLE rooms ALTER COLUMN visibility TYPE VARCHAR(20) USING visibility::text;
ALTER TABLE room_members ALTER COLUMN role TYPE VARCHAR(20) USING role::text;
ALTER TABLE friendships ALTER COLUMN status TYPE VARCHAR(20) USING status::text;

ALTER TABLE rooms ALTER COLUMN visibility SET DEFAULT 'public';
ALTER TABLE room_members ALTER COLUMN role SET DEFAULT 'member';
ALTER TABLE friendships ALTER COLUMN status SET DEFAULT 'pending';

DROP TYPE IF EXISTS room_visibility;
DROP TYPE IF EXISTS member_role;
DROP TYPE IF EXISTS friendship_status;

# --- !Downs

CREATE TYPE room_visibility  AS ENUM ('public', 'private');
CREATE TYPE member_role      AS ENUM ('owner', 'admin', 'member');
CREATE TYPE friendship_status AS ENUM ('pending', 'accepted', 'rejected');

ALTER TABLE rooms ALTER COLUMN visibility TYPE room_visibility USING visibility::room_visibility;
ALTER TABLE room_members ALTER COLUMN role TYPE member_role USING role::member_role;
ALTER TABLE friendships ALTER COLUMN status TYPE friendship_status USING status::friendship_status;
