# ADR 0005 — Phase 6 resilience hardening + custom metrics + tests

Status: accepted (Phase 5 approved, Phase 6 built 2026-11-24).

## Dead-letter queues
- Each Kafka service (chat/history/presence) defines a `CommonErrorHandler` bean: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `ExponentialBackOff(200ms, ×2, maxElapsed 2s)`. Boot wires the bean into the auto-configured listener container factory.
- Recoverer routes to `<topic>.DLT` **partition 0** (a single dead-letter partition is simple to inspect and avoids partition-count mismatch on auto-created topics).
- `.DLT` topics are **pre-created** (12 partitions) in `ops/kafka/create-topics.sh` because broker auto-create is disabled.
- Verified two ways: an `@EmbeddedKafka` integration test (poison command → DLT) and a **live probe** against the running stack.

## Sync retry
- chat→auth `RestClient` call wrapped in resilience4j `Retry` (3 attempts, 200ms wait, ignores `NotFound` and `CallNotPermittedException`) **outside** the circuit breaker. Transient failures retry; an open circuit fails fast to 503.

## Custom metrics (Micrometer → Prometheus)
- chat: `ws.connections.active` (gauge/instance, from SessionRegistry), `messages.sent` / `messages.delivered` (counters), `message.e2e.latency` (timer, percentiles 0.5/0.95/0.99), `rate.limit.rejected` (counter), `outbox.pending` (gauge).
- history: `outbox.pending` (gauge), `history.dedup.hits`, `history.messages.persisted` (counters).
- **Broker lag**: Spring Kafka binds consumer metrics (incl. `kafka_consumer_fetch_manager_records_lag{partition,topic}`) to the Micrometer global registry that Boot composites — no extra binder needed.
- **True e2e latency**: added `clientSentAt` to `MessagePersistedEvent`; chat records `now - clientSentAt` at delivery.

## Test infrastructure decision
- Testcontainers **Kafka via Docker** proved flaky here (advertised-listener networking; apache/kafka image wait-strategy mismatch). The DLQ test uses Spring Kafka's **`@EmbeddedKafka`** (in-JVM broker, reliable) while **Postgres stays on Testcontainers**. This is a test-harness choice, not a production change.

## Tests (26 project-wide)
- gateway 3, auth 10, chat 10 (added `ChatMessageHandlerMetricsTest` 2), history 3 (added `DeadLetterIntegrationTest` 1).

## Gate evidence (measured 2026-11-24)
- `mvn verify`: 26 green.
- Live DLQ: poison produced to `chat.message.command` → consumed from `chat.message.command.DLT`.
- chat-1: `message_e2e_latency_seconds_count=24`, `max≈0.45s`, `messages_delivered_total=24`.
- chat-2: `messages_sent_total=23`, `rate_limit_rejected_total=7` (30 rapid sends).
- `ws_connections_active`: chat-1=1.0 while a client connected, chat-2=0.0 (per-instance gauge responds live).
- history: `history_messages_persisted_total=24`, `outbox_pending=0`, per-partition `records_lag` present.

## Known limitations / next
- DLT uses partition 0; no automated DLT replay tool yet (manual replay is the documented runbook).
- Percentiles are client-side (Micrometer), not histogram buckets — fine for Grafana percentiles via `_max`/`_count`; Phase 8 dashboard uses these.
- Next: Phase 7 minimal frontend demo (two tabs, different instances).
