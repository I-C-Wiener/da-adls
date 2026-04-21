# /fix-bug

Diagnose and fix a bug.

## Arguments
$ARGUMENTS = bug description or error message

## Steps

1. **Reproduce** — find or write a failing test that demonstrates the bug
   - If the bug is in a service: add a test case to the relevant `*Spec.scala`
   - If the bug is in a controller: add an integration test
   - If the bug is in the frontend: add a `*.spec.ts` test
   - Run the test, confirm it fails

2. **Trace** — follow the call stack
   - Start from the entry point (route/controller/component)
   - Identify which layer has the wrong assumption
   - Check: is it the model, the repository query, the service logic, or the API contract?

3. **Fix** the root cause — not a symptom
   - Do not add try/catch to swallow errors
   - Do not add null checks that hide a deeper bug
   - Fix the actual invariant violation

4. **Verify**
   - The failing test now passes
   - All existing tests still pass: `sbt test` / `npm run test:ci`
   - Manual verify in browser if the fix affects UI behaviour

5. **Commit**
   ```bash
   git commit -m "fix: <description of what was wrong>"
   ```
