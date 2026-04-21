# /checkpoint

Run all checks and commit if everything is green.

## Steps

1. **Backend tests**
   ```bash
   cd backend && sbt test
   ```
   If red: stop, fix, re-run.

2. **Frontend tests**
   ```bash
   cd frontend && npm run test:ci
   ```
   If red: stop, fix, re-run.

3. **Type check frontend**
   ```bash
   cd frontend && npx tsc --noEmit
   ```

4. **Docker sanity**
   ```bash
   docker compose build --no-cache backend
   ```
   (Optional — skip if only frontend changed)

5. **Commit**
   ```bash
   git add <relevant files — never git add -A>
   git commit -m "<type>: <description>"
   ```
   Types: `feat`, `fix`, `refactor`, `test`, `chore`

## Never commit if:
- Any test is failing
- TypeScript has type errors
- You have uncommitted debug code or TODO comments that shouldn't be there
