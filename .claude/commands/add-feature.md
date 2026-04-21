# /add-feature

Implement a new feature following Harness Engineering rules.

## Arguments
$ARGUMENTS = feature name or description (e.g. "room bans" or "Phase 2")

## Steps

1. **Enter Plan Mode** (Shift+Tab) — describe the feature, identify files to change,
   write a 5-line spec. Exit Plan Mode when approved.

2. **Write failing tests first**
   - Backend: create or update the relevant `*Spec.scala` in `backend/test/`
   - Frontend: create or update the relevant `*.spec.ts`
   - Run tests, confirm they fail for the right reason

3. **Implement** the feature to make the tests pass
   - Follow existing patterns in the codebase
   - Use repositories for all DB access — never raw JDBC
   - Use Play Evolutions for any schema changes

4. **Verify**
   - `cd backend && sbt test` — all green
   - `cd frontend && npm run test:ci` — all green
   - Manual smoke test in browser

5. **Commit**
   - `git add <specific files>`
   - `git commit -m "feat: <feature name>"`
