# Phase 3 — Real-time Messaging

## Goal
Users connected via WebSocket receive messages in <3 seconds. Message history loads
with pagination (infinite scroll, 50 messages per page). Edit and delete work.
`expires_at` is set on every message insert based on `MESSAGE_EXPIRATION_DAYS`.

## Definition of Done
- [ ] Two browser tabs in the same room: send message → appears in both within 3 seconds
- [ ] Scroll up past 50 messages → next page loads
- [ ] Edit message shows "(edited)" label
- [ ] Delete message removes it from view
- [ ] `expires_at` is set correctly on inserted messages
- [ ] `sbt test` green (WebSocketActorSpec, MessageServiceSpec)

## Tests to Write First

```scala
// backend/test/services/MessageServiceSpec.scala
"save message" → sets expires_at = now + expirationDays
"save message when expirationDays=0" → expires_at is None
"edit message by sender" → updates content and isEdited flag
"edit message by non-sender" → returns Left("forbidden")
"delete message by sender" → soft-deletes
"paginate messages" → returns 50 records ordered by createdAt desc
```

## Implementation Steps

1. Complete `MessageRepository`: insert (with expires_at), findPage, softDelete
2. Create `MessageService`: save, edit, delete, paginate — validates sender ownership
3. Inject `MessageService` into `WebSocketActor.handleSendMessage()`
4. Wire `RedisPublisher.publish(roomChannel, messageJson)` after save
5. `RedisSubscriber` already listens — add `_recipients` to published JSON (room member IDs)
6. Implement `MessageController.list()` with pagination params
7. Angular: `MessageService` — HTTP call for initial history load
8. Angular: `MessageAreaComponent` — virtual scroll with `CdkVirtualScrollViewport`,
   trigger load-more when scrolled near top (index < 5)
9. Angular: `MessageInputComponent` — textarea, send on Ctrl+Enter, reply support

## WebSocket Message Flow
```
Client sends: { type: "send_message", conversationId: 42, content: "hello" }
  → WebSocketActor.handleSendMessage()
  → MessageService.save() → MessageRepository.insert()
  → RedisPublisher.publish("room:42", { type: "message_received", message: {...}, _recipients: [1,2,3] })
  → RedisSubscriber receives, calls ActorRegistry.broadcastToRoom([1,2,3], json)
  → All WebSocketActors for those users receive Broadcast(json)
  → out ! json → browser receives { type: "message_received", message: {...} }
```

## Key Files
- `backend/app/services/MessageService.scala` (create)
- `backend/app/actors/WebSocketActor.scala` (complete handleSendMessage)
- `backend/app/pubsub/RedisPublisher.scala`
- `backend/app/pubsub/RedisSubscriber.scala`
- `frontend/src/app/core/api/message.service.ts` (create)
- `frontend/src/app/features/chat/message-area/message-area.component.ts`
