# Online Chat Server

A real-time web chat application supporting public and private rooms, direct messaging, contacts, file sharing, and presence indicators.

## Quick Start

```bash
docker compose up --build
```

Open **http://localhost** in your browser.

> First start takes a few minutes while sbt downloads dependencies and compiles the backend.  
> Database schema is applied automatically via Play Evolutions on startup.

### Full clean start

```bash
docker compose down -v
docker compose up --build
```

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Scala 2.13, Play Framework 2.9, Akka actors |
| Database | PostgreSQL 16 (schema managed by Play Evolutions) |
| Realtime | WebSockets + Redis 7.2 pub/sub fan-out |
| Auth | JWT (HS256) + bcrypt password hashing |
| Frontend | Angular 17 (standalone components, strict TypeScript) |
| Proxy | nginx (routes `/api/*` and `/ws` to backend, serves SPA) |
| Storage | Local filesystem volume (`/uploads`) |

---

## Features

### Accounts & Auth
- Register with email, username, and password
- Sign in / sign out (per-session logout; other sessions unaffected)
- Persistent login across browser restarts
- Password reset via email token
- Password change for logged-in users
- Account deletion (owned rooms and their data deleted; other memberships removed)

### Presence
- Online / AFK / offline status per user
- AFK after 1 minute of inactivity in all open tabs
- Multi-tab support: online if any tab is active
- Presence updates propagate within 2 seconds

### Sessions
- View all active sessions with browser and IP details
- Revoke individual sessions remotely

### Rooms
- Create public or private rooms
- Public room catalog with search and member counts
- Join public rooms freely; private rooms require invitation
- Leave rooms (owner must delete, not leave)
- Room owner and admin roles with full moderation controls
- Ban / unban members; ban list shows who banned whom and when
- Room settings: name, description, visibility

### Direct Messaging
- One-to-one personal chats with friends
- Personal chats are blocked if either user has banned the other

### Contacts / Friends
- Send friend requests by username or from a room member list (with optional message)
- Accept / reject incoming requests; pending count shown in top nav
- Remove friends
- Block / unban users

### Messaging
- Real-time delivery via WebSocket
- Multiline text, emoji picker, file attachments
- Reply to a specific message (quoted inline)
- Edit own messages (shown with "· edited" indicator)
- Delete messages (own messages; admins can delete any message in their room)
- Infinite scroll for message history
- Messages delivered to offline users on next login

### Attachments
- Upload via button or paste from clipboard (staged preview before send)
- Images: max 3 MB; other files: max 20 MB
- Original filename and optional caption preserved
- Access restricted to current room members / DM participants

### Notifications
- Unread message badges on room names and contact names
- Badge cleared when the conversation is opened

---

## Configuration

All settings are environment variables with defaults that work out of the box.

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_PASSWORD` | `chat_secret` | PostgreSQL password |
| `JWT_SECRET` | `change_me_in_production` | HS256 signing secret |
| `JWT_EXPIRY_HOURS` | `72` | JWT lifetime in hours |
| `APPLICATION_SECRET` | `change_me_in_production_32chars_min` | Play CSRF/session secret |
| `MESSAGE_EXPIRATION_DAYS` | `30` | Days before messages are purged (0 = never) |
| `MAX_FILE_SIZE_MB` | `20` | Maximum attachment size |
| `MAX_IMAGE_SIZE_MB` | `3` | Maximum image size |

Override any variable by creating a `.env` file next to `docker-compose.yml`:

```env
JWT_SECRET=my_strong_secret
APPLICATION_SECRET=my_strong_app_secret_32_chars_min
POSTGRES_PASSWORD=my_db_password
MESSAGE_EXPIRATION_DAYS=7
```

---

## Architecture

```
Browser
  │
  ▼
nginx :80
  ├── /api/*  ──► Play backend :9000 (REST)
  ├── /ws     ──► Play backend :9000 (WebSocket)
  └── /*      ──► Angular SPA (index.html)

Play backend
  ├── Akka actor per WebSocket connection
  ├── Redis pub/sub for cross-instance message fan-out
  ├── Slick (async) for all DB access
  └── Play Evolutions for schema migrations

PostgreSQL  ◄──  Slick repositories
Redis       ◄──  Lettuce client (pub/sub)
/uploads    ◄──  Docker volume (file attachments)
```

### WebSocket events

| Direction | Event | Description |
|---|---|---|
| Client → Server | `auth` | Authenticate with JWT |
| Client → Server | `send_message` | Send a message |
| Client → Server | `edit_message` | Edit own message |
| Client → Server | `delete_message` | Delete a message |
| Client → Server | `mark_read` | Clear unread count |
| Client → Server | `ping` | Heartbeat (presence) |
| Client → Server | `typing_start` | Typing indicator |
| Server → Client | `message_received` | New message |
| Server → Client | `message_edited` | Message was edited |
| Server → Client | `message_deleted` | Message was deleted |
| Server → Client | `presence_update` | User presence changed |
| Server → Client | `unread_update` | Unread count changed |
| Server → Client | `auth_ok` | Authentication accepted |

---

## Project Structure

```
da-adls/
├── docker-compose.yml
├── backend/                   # Play / Scala application
│   ├── app/
│   │   ├── actors/            # WebSocketActor, presence
│   │   ├── controllers/       # REST + WebSocket endpoints
│   │   ├── models/            # Domain models
│   │   ├── repositories/      # Slick data access
│   │   ├── services/          # Business logic
│   │   └── pubsub/            # Redis pub/sub
│   └── conf/
│       ├── routes
│       ├── application.conf
│       └── evolutions/default/ # SQL migrations (1–8)
└── frontend/                  # Angular 17 application
    ├── nginx.conf
    └── src/app/
        ├── core/              # Auth, WebSocket, Presence, API services
        ├── features/          # Auth, Chat, Rooms, Contacts, Sessions, Profile
        └── shared/            # Pipes, components
```

---

## Development (without Docker)

**Backend** — requires JDK 17+ and sbt:
```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5432/chatdb \
DB_USER=chat \
DB_PASSWORD=chat_secret \
REDIS_HOST=localhost \
sbt run
```

**Frontend** — requires Node 20+:
```bash
cd frontend
npm install
npm start        # dev server on http://localhost:4200 (proxies /api and /ws to :9000)
```
