# Phase 0 — Project Initialization

## Goal
`docker compose up` starts all 4 services (postgres, redis, backend, frontend).
Play Evolutions run automatically. `GET /api/health` returns `{"status":"ok"}`.
Angular app loads in the browser at `http://localhost`.

## Definition of Done
- [ ] All 4 Docker services start without errors
- [ ] `curl http://localhost/api/health` → `{"status":"ok"}`
- [ ] Browser at `http://localhost` shows the Angular login screen
- [ ] `sbt test` passes (HealthControllerSpec)

## Implementation Steps

1. Verify `backend/build.sbt` dependencies compile: `cd backend && sbt compile`
2. Fix any compilation errors before moving on
3. Verify `frontend/` builds: `cd frontend && npm ci && npm run build:prod`
4. Build Docker images: `docker compose build`
5. Start stack: `docker compose up -d`
6. Check logs: `docker compose logs -f backend`
7. Verify evolutions applied (look for "Applying evolutions" in backend logs)
8. Smoke test: `curl http://localhost/api/health`

## Tests to Write First
`backend/test/controllers/HealthControllerSpec.scala` — already scaffolded.
Run: `cd backend && sbt test`
