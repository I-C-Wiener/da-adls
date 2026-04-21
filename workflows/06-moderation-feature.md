# Phase 6 — Contacts, DMs, and Moderation

## Goal
Friend requests, personal messaging, and user-level bans work.
Room admin moderation (ban/unban member, promote/demote) works from the UI.

## Definition of Done
- [ ] Send friend request → recipient sees notification in WS
- [ ] Accept request → DM conversation available
- [ ] DM messages flow through same WS pipeline as room messages
- [ ] User ban blocks DM (conversation `frozen=true`)
- [ ] Room admin can ban member; member's WS subscription to that room is dropped
- [ ] Room admin can promote/demote members
- [ ] `sbt test` green (ContactServiceSpec)

## Tests to Write First

```scala
// backend/test/services/ContactServiceSpec.scala
"send friend request" → creates pending friendship
"accept friend request" → creates DM conversation, status = accepted
"reject friend request" → status = rejected
"block user" → creates user_ban row, freezes any existing DM
"remove contact" → deletes friendship row
"send DM to non-friend" → returns Left("not friends")
```

## Implementation Steps

1. Create `ContactRepository`: friendships CRUD, user_bans, dm_conversations
2. Create `ContactService`: sendRequest, respond, block, unblock, remove
3. On accept: create `conversations` row (type='dm') and `dm_conversations` row
4. Implement `ContactController` — replace stubs
5. Notify via WebSocket on friend request accepted: `{ type: "friend_request_accepted", ... }`
6. Room moderation: `RoomController.ban()` should also close active WebSocket
   subscriptions for that user+room (call `ActorRegistry.dropRoomSubscription()`)
7. Angular: `ContactsComponent` — list friends, pending requests, send request form
8. Angular: `SidebarComponent` — add DM conversations section
9. Angular: `ManageRoomComponent` — tabs: Members, Bans, Settings

## Key Files
- `backend/app/repositories/FriendshipRepository.scala` (create)
- `backend/app/services/ContactService.scala` (create)
- `backend/app/controllers/ContactController.scala`
- `frontend/src/app/core/api/contact.service.ts` (create)
- `frontend/src/app/features/contacts/contacts.component.ts`
- `frontend/src/app/features/rooms/manage-room/manage-room.component.ts`
