# Final Submission Checklist

## Deadline: April 22, 2026

## Pre-Submission Checks

### Correctness
- [ ] `docker compose down -v && docker compose up --build` starts cleanly from scratch
- [ ] All 4 services healthy after startup
- [ ] Browser loads Angular app at `http://localhost`
- [ ] Golden path: register → login → create room → send message → see it in another tab
- [ ] File upload and download work
- [ ] Presence badges update correctly
- [ ] Friend request + DM flow works

### Code Quality
- [ ] `cd backend && sbt test` — zero failing tests
- [ ] `cd frontend && npm run test:ci` — zero failing tests
- [ ] No hardcoded secrets or credentials in any file
- [ ] `.gitignore` excludes `.env`, `uploads/`, `target/`, `node_modules/`, `dist/`

### Repository
- [ ] `.env.example` documents all environment variables
- [ ] `README.md` created with: project description, `docker compose up` instructions, env var table
- [ ] GitHub repo is public (or shared with reviewers)
- [ ] All commits have descriptive messages

### Performance Spot Check
- [ ] Open 5 browser tabs as 5 different users in the same room
- [ ] Send a message — appears in all 5 tabs within 3 seconds
- [ ] Check presence badges update within 2 seconds of tab change

## README Template

```markdown
# Online Chat Server

A production-ready real-time chat application.

## Stack
- Backend: Scala 2.13 / Play Framework 2.9 / Slick 3.5 / Akka
- Frontend: Angular 17 (standalone components)
- Database: PostgreSQL 16
- Realtime: WebSockets + Redis 7.2 pub/sub
- Proxy: nginx

## Quick Start

\`\`\`bash
cp .env.example .env
docker compose up --build
\`\`\`

Open http://localhost in your browser.

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| MESSAGE_EXPIRATION_DAYS | 30 | Days before messages are purged (0 = never) |
| JWT_SECRET | change_me | HS256 signing secret |
| POSTGRES_PASSWORD | chat_secret | DB password |
| APPLICATION_SECRET | change_me | Play CSRF secret |
| MAX_FILE_SIZE_MB | 20 | Max attachment size |
| MAX_IMAGE_SIZE_MB | 3 | Max image size |

## Features
- User registration and authentication (JWT)
- Public and private chat rooms
- Real-time messaging via WebSocket
- Presence indicators (online/AFK/offline), multi-tab support
- File and image sharing (access-controlled)
- One-to-one messaging with friend requests
- Message history with infinite scroll
- Configurable message expiration
- Room moderation (bans, admin roles)
\`\`\`
