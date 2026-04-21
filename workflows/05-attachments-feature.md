# Phase 5 — File and Image Attachments

## Goal
Users upload files (≤20MB) and images (≤3MB) as message attachments.
Download is gated on current room membership or DM participation.

## Definition of Done
- [ ] Upload 1MB image → thumbnail appears in chat within 3 seconds
- [ ] File > 20MB rejected with 413 or 400 error
- [ ] Image > 3MB rejected with 400 error
- [ ] Banned user cannot download attachment via direct URL (403)
- [ ] `sbt test` green (AttachmentServiceSpec)

## Tests to Write First

```scala
// backend/test/services/AttachmentServiceSpec.scala
"upload file within limit" → returns attachment metadata with storedPath
"upload file exceeding maxFileMb" → returns Left("file too large")
"upload image exceeding maxImageMb" → returns Left("image too large")
"download attachment as room member" → returns file stream
"download attachment as non-member" → returns Left("forbidden")
```

## Implementation Steps

1. Create `AttachmentService`:
   - `save(messageId, multipartFile, mimeType, size)` → validates size, stores to
     `{uploadDir}/{roomId}/{uuid}-{originalName}`, inserts into `attachments` table
   - `stream(attachmentId, requestingUserId)` → checks membership, opens file stream
2. Implement `AttachmentController.upload()`: parse multipart, validate, save, return JSON metadata
3. Implement `AttachmentController.download()`: stream file with correct Content-Type,
   set `Content-Disposition: attachment; filename="..."`
4. Mime type detection: check Content-Type header; for images verify with magic bytes
5. Angular: `AttachmentService` — upload with `HttpClient` `reportProgress: true` for progress bar
6. Angular: `AttachmentPreviewComponent` — image inline vs file-download link by mime type
7. Angular: Paste handler in `MessageInputComponent` — `(paste)` event + `ClipboardEvent.clipboardData`

## Storage Layout
```
/uploads/
  {conversationId}/
    {uuid}-{originalName}   ← actual file
```

## Key Files
- `backend/app/services/AttachmentService.scala` (create)
- `backend/app/controllers/AttachmentController.scala`
- `backend/app/repositories/AttachmentRepository.scala` (create)
- `frontend/src/app/core/api/attachment.service.ts` (create)
- `frontend/src/app/shared/components/attachment-preview/attachment-preview.component.ts`
