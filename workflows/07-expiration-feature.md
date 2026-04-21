# Phase 7 — Message Expiration

## Goal
Messages older than `MESSAGE_EXPIRATION_DAYS` are purged daily at midnight UTC.
Setting `MESSAGE_EXPIRATION_DAYS=0` disables expiration entirely.
Orphaned attachment files are cleaned from disk after their messages are purged.

## Definition of Done
- [ ] Message inserted with `expires_at = now + N days` (verified in DB)
- [ ] `MessageExpirationJob` runs daily and purges expired rows
- [ ] Orphaned files in `/uploads` are removed after purge
- [ ] `MESSAGE_EXPIRATION_DAYS=0` → no `expires_at` set, job logs "disabled"
- [ ] `sbt test` green (MessageExpirationServiceSpec already scaffolded)

## Tests to Write First

Already scaffolded in `backend/test/services/MessageExpirationServiceSpec.scala`.
Add integration test using embedded postgres:

```scala
// backend/test/repositories/MessageRepositorySpec.scala
"insert message with expirationDays=30" → expires_at ≈ now + 30 days
"deleteExpired deletes row with expires_at in the past" → row gone
"deleteExpired keeps row with expires_at in the future" → row remains
"deleteExpired with no expired rows" → returns 0
```

## Implementation Steps

1. Verify `MessageExpirationJob` is registered as eager singleton in `Module.scala` ✓
2. Verify `MessageService.save()` sets `expires_at` from config (expirationDays > 0)
3. Complete `AttachmentService.cleanOrphaned()`:
   - After `MessageRepository.deleteExpired()`, query `attachments` for deleted message IDs
   - Delete files from disk using `java.nio.file.Files.deleteIfExists()`
4. Add dev-only trigger endpoint for manual testing:
   `GET /api/admin/trigger-expiration` (guarded by secret header, only in dev mode)
5. Write integration test with embedded postgres:
   insert message with `expires_at = Instant.now().minusSeconds(1)`,
   call `deleteExpired()`, assert row count = 0

## Verification (manual)
```bash
# Set short expiration for testing
MESSAGE_EXPIRATION_DAYS=1 docker compose up -d

# Insert an already-expired message directly
docker compose exec postgres psql -U chat chatdb -c \
  "INSERT INTO messages(conversation_id, sender_id, content, created_at, expires_at) \
   VALUES (1, 1, 'test', NOW(), NOW() - INTERVAL '1 hour');"

# Trigger the job
curl -H "X-Admin-Secret: dev" http://localhost/api/admin/trigger-expiration

# Verify gone
docker compose exec postgres psql -U chat chatdb -c "SELECT count(*) FROM messages WHERE content='test';"
```

## Key Files
- `backend/app/jobs/MessageExpirationJob.scala`
- `backend/app/services/MessageExpirationService.scala`
- `backend/app/repositories/MessageRepository.scala` (deleteExpired method)
- `backend/app/services/AttachmentService.scala` (cleanOrphaned method)
- `backend/test/repositories/MessageRepositorySpec.scala` (create)
