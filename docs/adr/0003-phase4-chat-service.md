# ADR 0003 — Phase 4 chat-service (real-time relay + conversation aggregate)

Status: accepted (Phase 3 approved, Phase 4 built 2026-11-24).

## Transport & destinations
- **STOMP over native WebSocket** at `/ws` (no SockJS, A6). Application prefix `/app`, user prefix `/user`, simple broker `/queue`.
- **User destinations** (`/user/queue/messages|acks|errors|typing|conversations|receipts`) instead of public `/topic/conversation.{id}`. Fan-out uses `convertAndSendToUser(userId, ...)`, so a client can only ever receive its own queue — no subscription-authorization interceptor needed. Send destinations: `/app/chat.send`, `/app/chat.typing`, `/app/chat.receipt`.

## WebSocket auth (defense in depth)
- Gateway promotes `?access_token=` → `Authorization` on the upgrade (Phase 3). Chat's `JwtHandshakeInterceptor` captures it into session attributes; `AuthChannelInterceptor` validates the JWT on STOMP **CONNECT** with its own `JwtDecoder` and sets `Principal = userId`. SEND/SUBSCRIBE without an authenticated user are rejected.

## Broadcast fan-out
- Each instance consumes with **per-instance, per-topic consumer groups** `chat-fanout-{instanceId}-{msg|conv|typing|receipt}`, `auto.offset.reset=latest`. Every instance sees every event and delivers only to members with a **local** session (`SessionRegistry`). This is the stateless alternative to sticky routing: any instance serves any user.
- **Persist-before-fanout**: chat produces `chat.message.command`; history-service (Phase 5) dedups + assigns seq + persists + publishes `chat.message.persisted`; chat consumes that and fans out. The Phase 4 gate drove fan-out with a **persisted-event fixture** (history is Phase 5) — stated honestly, not implied end-to-end.

## Conversation aggregate (chatdb)
- `conversations` + `conversation_members` owned here so membership checks are a local indexed lookup at line rate. 1:1 is **idempotent find-or-create** via `direct_key` = sorted `uuidA:uuidB` (**VARCHAR(80)** — two UUIDs + colon = 73 chars; fixed from an initial 64).
- Conversation/membership changes write an **outbox** row in the same transaction; `OutboxPoller` claims with `FOR UPDATE SKIP LOCKED`, publishes to Kafka, and only marks published after a blocking broker confirm (`.get(5s)`), so a crash never loses an event.
- Group caps: 256 members; add/remove require OWNER/ADMIN (self-remove allowed); invitees verified via auth-service.

## REST user context
- Controllers read the gateway's **`X-User-Id` header directly** (`@RequestHeader`), not a SecurityContext `Principal`. Explicit and reliable; chat REST is `permitAll` because the gateway is the auth boundary and the service is internal-only. (An initial SecurityContext/Principal approach resolved null — replaced with the header.)

## Sync call: chat → auth
- `@LoadBalanced RestClient` → `http://auth-service/internal/users/{id}`, 1s connect/read timeouts, wrapped in a Resilience4j `CircuitBreaker` that **ignores 404** (a normal "user absent" answer, not a failure). Open circuit → 503.

## WS rate limiting
- Redis **Lua token bucket** using server `TIME` (no client clock skew), per user, capacity 20 / refill 20 per second. **Fail-open** if Redis is down. Excess sends get a `/queue/errors` frame.

## Receipts
- On pushing a persisted message to a non-sender member, chat produces a `DELIVERED` receipt (`upToSeq`). History consumes receipts into watermarks in Phase 5.

## Gotchas hit (and fixed)
- `messageBrokerTaskScheduler` bean name collides with Spring's auto-configured broker scheduler → renamed `webSocketBrokerScheduler`.
- `direct_key` VARCHAR(64) too short for two UUIDs → widened to 80 (live `ALTER` + init SQL + entity).
- REST `Principal` from SecurityContext resolved null → switched to `@RequestHeader("X-User-Id")`.

## Tests (8 chat; 21 project-wide)
- `ConversationServiceTest` (5): self-chat rejected, idempotent direct, group needs another member, add requires admin, add verifies user exists.
- `AuthChannelInterceptorTest` (3): CONNECT valid sets user, CONNECT no token rejected, SEND unauthenticated rejected.

## Gate evidence (measured 2026-11-24, headless STOMP via gateway :18080)
- Two users registered; DIRECT conversation created (2 members); repeat create returned the **same id**.
- alice SEND → `chat.message.command` observed in Kafka with the client messageId.
- Persisted fixture (seq 1) → **both** alice and bob received `/user/queue/messages`; alice on **chat-2**, bob on **chat-1** (cross-instance fan-out confirmed via connect logs).
- "sent" ack, typing fan-out to both, DELIVERED receipt fan-out all observed.
- Rate limit: 40 rapid sends → **16 rate-limited + 24 acks**.
- `mvn -pl chat-service verify`: 8 tests green. Both chat replicas healthy.

## Known limitations / next
- Full command→persist→fan-out loop closes in Phase 5 (history-service).
- Receipts are produced but not yet aggregated into read/delivered watermarks (Phase 5).
- Presence connection events produced; presence-service consumes in Phase 5.
- One presence mapping per user; offline gap-sync is Phase 5.
