# Rules

- Always ask clarifying questions before starting a complex task
- Show your plan and steps before executing
- Cite sources when doing research

# My preferred technoligies
- docker
- scala for backend
- postresql as db
- angular for UI
- websockets with queing mechanism

# Project Structure

- workflows/ – Workflow instruction files (plain English recipes the agent follows)
- output/ – Finished deliverables (reports, drafts, analysis)
- resources/ – Reference docs and templates

# Diverse app notes
- Make configurable experation period for chat messages (1 month by default).

---

## Task: Online Chat Server (Hackathon — Deadline Apr 22, 2026)

### Architecture

| Layer | Choice |
|---|---|
| Backend | Play Framework 2.9 (Scala 2.13), Slick 3.5, Akka actors |
| Frontend | Angular 17 (standalone components, strict TypeScript) |
| Database | PostgreSQL 16 (schema via Play Evolutions) |
| Realtime | WebSockets via Play + Akka; Redis 7.2 pub/sub for fan-out |
| Auth | JWT (com.auth0:java-jwt 4.4) + jBCrypt password hashing |
| Storage | Local filesystem volume at /uploads |
| Proxy | nginx in frontend container — routes /api/* and /ws to backend:9000 |

### GSD Milestones (implement in order)

1. Phase 0 — Project init: `docker compose up` boots all 4 services, `/api/health` returns 200
2. Phase 1 — Auth: register, login, JWT, sessions, password reset
3. Phase 2 — Rooms: create, join, leave, public catalog, member management, bans, invitations
4. Phase 3 — Messaging: real-time WebSocket, Redis fan-out, message history pagination
5. Phase 4 — Presence: online/AFK/offline, multi-tab heartbeat, <2s propagation
6. Phase 5 — Attachments: file/image upload + download with membership access control
7. Phase 6 — Contacts & DMs: friend requests, personal messaging, user bans
8. Phase 7 — Moderation: room admin actions, expiration job
9. Submission checklist: cold-start smoke test, green tests, README

### Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| MESSAGE_EXPIRATION_DAYS | 30 | Days before messages are purged (0 = never) |
| JWT_SECRET | change_me | HS256 signing secret |
| JWT_EXPIRY_HOURS | 72 | JWT lifetime |
| POSTGRES_PASSWORD | chat_secret | DB password |
| APPLICATION_SECRET | change_me | Play CSRF/session secret |
| MAX_FILE_SIZE_MB | 20 | Max attachment size |
| MAX_IMAGE_SIZE_MB | 3 | Max image size |

### Rules for Claude

- Write a failing test before implementing any feature (Harness Engineering)
- Run `sbt test` after every backend change; `ng test --watch=false` after frontend changes
- Never commit red tests
- Use Play Evolutions for all schema changes — never ALTER TABLE manually
- All DB access via Slick repositories — no raw JDBC in controllers
- WebSocket messages are JSON; define event types in ws-event.types.ts and sealed traits in Scala
- JWT secret comes from environment — never hardcode
- Attachment download routes must verify room/DM membership before streaming the file
- MESSAGE_EXPIRATION_DAYS=0 means messages never expire (no expires_at set)
- Run `docker compose up --build` and smoke-test before marking any milestone done

### Non-Negotiables

- `docker compose up` must work cold from a fresh clone (no pre-existing volumes required)
- Play Evolutions auto-apply on backend startup
- Presence updates propagate within 2 seconds
- Message delivery completes within 3 seconds
- Scale target: 300 simultaneous users, up to 1,000 per room