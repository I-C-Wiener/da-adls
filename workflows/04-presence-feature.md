# Phase 4 — Presence

## Goal
Online/AFK/offline status is visible in the member list and contact list. Status
updates propagate within 2 seconds. Multi-tab AFK logic works correctly.

## Definition of Done
- [ ] Open tab → appear online in second tab's member list within 2 seconds
- [ ] Go idle 70 seconds → status changes to AFK
- [ ] Close all tabs → status changes to offline within ~90 seconds
- [ ] Member list shows presence badge (green/amber/grey) per user
- [ ] `sbt test` green (PresenceServiceSpec)

## Tests to Write First

```scala
// backend/test/services/PresenceServiceSpec.scala
"single tab active" → derivePresence = Online
"single tab idle (activeTab=false)" → derivePresence = Afk
"no ping for 90s" → derivePresence = Offline (simulate stale lastPing)
"two tabs: one active, one idle" → derivePresence = Online
"all tabs idle" → derivePresence = Afk
```

## Implementation Steps

1. `PresenceService.computeAndBroadcast(userId)` — derives status from ActorRegistry,
   if status changed: publish to `presence:{userId}` channel + update `user_presence` table
2. Call `computeAndBroadcast` from `WebSocketActor.handlePing()` after updating registry
3. Call `computeAndBroadcast` from `WebSocketActor.postStop()` after unregistering
4. `RedisSubscriber` subscribes to `presence:*` channels (use psubscribe pattern)
5. On presence event received, `ActorRegistry.broadcastToUser(userId, presenceJson)`
   where the JSON includes `_recipients` = list of users who should receive it
   (all room members + contacts of this user)
6. Angular: `PresenceService` already scaffolded — wire to WS stream
7. Angular: `PresenceBadgeComponent` already scaffolded — use in sidebar member list
8. Angular: `AfkDetectorService` already scaffolded — start in `ChatLayoutComponent`

## Note on Broadcast Recipients
When presence changes, the server must know who to notify. Options:
- **Simple**: broadcast to all currently connected users (easy, possibly over-notify)
- **Precise**: query room memberships + friendship list to get recipients

For hackathon: use simple approach first (broadcast to all connected users in the registry).

## Key Files
- `backend/app/services/PresenceService.scala` (create)
- `backend/app/actors/WebSocketActor.scala` (add presence broadcast calls)
- `backend/app/pubsub/ActorRegistry.scala`
- `frontend/src/app/core/presence/presence.service.ts`
- `frontend/src/app/core/presence/afk-detector.service.ts`
- `frontend/src/app/shared/components/presence-badge/presence-badge.component.ts`
