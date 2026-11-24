# ADR 0004 — Phase 5 history-service, presence, receipts, offline sync

Status: accepted (Phase 4 approved, Phase 5 built 2026-11-24). Closes the end-to-end message loop.

## history-service (system of record)
- **Command consumer** group `history`, `auto-offset-reset=earliest` (must not miss commands produced before it started). Kafka partition key = conversationId → one consumer per partition → **single writer per conversation**, so check-then-increment is race-free.
- **Ingest** (`MessageIngestService`, one transaction): dedup via `select count(*) ... client_message_id` (skip + `history.dedup.hits` metric if present); ensure `conversation_state` row; **atomic seq** `UPDATE conversation_state SET last_seq=last_seq+1 ... RETURNING last_seq` (JdbcTemplate); `INSERT INTO messages ... ON CONFLICT DO NOTHING`; write `chat.message.persisted` to the **outbox**.
- **Outbox poller**: `FOR UPDATE SKIP LOCKED`, publish, mark published only after blocking broker confirm. Same pattern as chat-service.
- **Membership projection** consumer (`history-conv`) applies CREATED/MEMBER_ADDED/MEMBER_REMOVED → `membership_projection`, used for **read authorization** (eventually consistent; a removed member may read for the propagation window).
- **Receipt consumer** (`history-receipt`) upserts `read_state` with `GREATEST` so watermarks are monotonic; READ implies delivered.
- **Query API**: `GET /history/conversations/{id}/messages?afterSeq&limit` (keyset `seq > afterSeq ORDER BY seq LIMIT`, bounded ≤200) doubles as **offline gap-sync**; `GET .../state` returns the caller's watermarks. Both require membership and read the gateway `X-User-Id` header.

## presence-service
- Consumes `chat.presence.connection` (group `presence`, latest). Redis `presence:{userId}` = instanceId, **TTL 30s**. CONNECTED sets + publishes `presence.changed` online **only on transition**; HEARTBEAT refreshes TTL; DISCONNECTED deletes + publishes offline.
- REST `GET /presence/{userId}` and `GET /presence?ids=` (batch) for contact presence.
- **chat-service heartbeat**: `@Scheduled(10s)` publishes HEARTBEAT for every local session user so long-lived connections aren't shown offline. chat consumes `presence.changed` and pushes to the user's **own** sessions (`/queue/presence`); contacts query the REST API.

## Offset policy (deliberate per role)
- history `earliest` (durability, replay), chat fan-out `latest` (live only; reconnects use history sync), presence `latest` (ephemeral).

## Tests
- `HistoryIngestIntegrationTest` (Testcontainers Postgres, listeners disabled): dedup idempotent + monotonic seq + per-conversation seq + outbox count. **Fixed a test-isolation bug**: assertions are per-conversation (`pageAfter`/`countByAggregateId`), not global counts that leak across methods.
- 23 tests green project-wide (gateway 3, auth 10, chat 8, history 2).

## Gate evidence (measured 2026-11-24, end-to-end through gateway :18080, NO fixture)
- alice SEND → bob received `/user/queue/messages` seq 1 "e2e hello" (history closed command→persist→fan-out).
- Duplicate resend → history message count **1** (dedup).
- Offline: bob disconnected, alice sent M2/M3 → bob gap-sync `afterSeq=1` returned **seq 2, 3**.
- Pagination `afterSeq=1&limit=1` → only seq 2.
- READ receipt upToSeq=3 → `lastReadSeq=3, lastDeliveredSeq=3`.
- Presence: offline → online (instanceId populated) → offline.

## Known limitations / next
- Typing indicators were completed in Phase 4 (produced + fanned out); no new work here.
- Presence fan-out is to own sessions; contact presence is pull-based (REST). Multi-device presence not modeled.
- Read/delivered are conversation-level watermarks, not per-message rows (by design, ADR 0001 §4.8).
- Next: Phase 6 resilience hardening (DLQ, retries, custom metrics) + broader tests.
