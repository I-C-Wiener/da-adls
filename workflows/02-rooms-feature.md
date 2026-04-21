# Phase 2 — Chat Rooms

## Goal
Users can create public/private rooms, browse the catalog, join/leave, and owners/admins
can manage members, bans, and invitations.

## Definition of Done
- [ ] `GET /api/rooms` returns public rooms (with optional ?search=)
- [ ] `POST /api/rooms` creates a room and auto-joins the creator as owner
- [ ] `POST /api/rooms/:id/join` adds user as member (fails if banned)
- [ ] `POST /api/rooms/:id/leave` removes member (fails if owner — must transfer first)
- [ ] Room ban prevents rejoin
- [ ] Private room requires invitation to join
- [ ] `sbt test` green (RoomServiceSpec, RoomControllerSpec)

## Tests to Write First

```scala
// backend/test/services/RoomServiceSpec.scala
"create room" → creator is owner member
"join public room" → user becomes member
"join room when banned" → returns Left("banned")
"leave room" → member removed
"owner cannot leave without transfer" → returns Left("transfer ownership first")
"delete room cascades memberships" → no orphan rows
```

## Implementation Steps

1. Create `RoomRepository` with: create, findById, findPublic(search), addMember, removeMember,
   findMembers, ban, unban, isBanned, isMember, createInvitation
2. Create `RoomService` — thin delegation to repository + business rules
3. Implement `RoomController` — replace stub methods with real service calls
4. Each room creation → also insert a row in `conversations` table (type='room')
5. Angular: `RoomService` (HTTP calls for list, create, join, leave)
6. Angular: `RoomCatalogComponent` — displays public rooms with search
7. Angular: `CreateRoomComponent` — dialog with name/visibility/description fields
8. Angular: `SidebarComponent` — show user's joined rooms, link to `/chat/room/:id`

## Key Files
- `backend/app/repositories/RoomRepository.scala` (create)
- `backend/app/services/RoomService.scala` (create)
- `backend/app/controllers/RoomController.scala`
- `frontend/src/app/core/api/room.service.ts` (create)
- `frontend/src/app/features/chat/sidebar/sidebar.component.ts`
