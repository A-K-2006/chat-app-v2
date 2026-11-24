# Relay — architectural decisions & patterns (study guide)

A plain-language summary of the decisions and patterns in this project, for reading and interview
prep. Each item: **pattern → what we did → why → trade-off**. Formal records are in `docs/adr/`;
the full proposal is `docs/architecture.md`; diagrams in `docs/architecture-diagram.md`.

**Big picture:** event-driven microservices with a CQRS-style read model. Chat is the real-time
write path; history is the durable, eventually-consistent read model; they communicate through Kafka.

---

## 1. Decomposition & data ownership

**Microservices with bounded contexts.**
Four business services (auth, chat, history, presence), split by *reason to change and scale*, not by
nouns: chat = stateful real-time edge, history = durable read model, presence = ephemeral state,
auth = security boundary.
*Why:* independent scaling (WS connections vs read throughput vs BCrypt CPU) and independent deploy.
*Trade-off:* operational complexity + eventual consistency — handled explicitly, not hidden.

**Database-per-service.**
No shared tables: `authdb`, `chatdb`, `historydb`, Redis for presence.
*Why:* forces loose coupling — services can't read each other's data, so they must use APIs/events.
*Trade-off:* no joins across services; you denormalize/project instead (see membership projection).

> Interview: "Why not a modular monolith?" → independent scaling/deploy and it demonstrates the
> distributed problems you then solve. The cost is complexity, which we address with the patterns below.

## 2. Edge & communication

**API Gateway pattern.**
Spring Cloud Gateway is the single entry point: JWT validation, per-user rate limiting, CORS, circuit
breakers, routing.
*Why:* cross-cutting concerns live once at the edge, not in every service.

**Service discovery + client-side load balancing.**
Eureka + `lb://` URIs; stateless services register dynamically; gateway round-robins instances.
*Why:* horizontal scaling without hardcoded hosts. *Trade-off:* Eureka adds a component (vs Docker DNS).

**Async-first, sync-where-necessary.**
Almost everything is Kafka events; exactly one sync call (chat → auth on invite, to verify the invitee
exists before committing membership), wrapped in timeout + retry + circuit breaker.
*Why:* sync calls create runtime coupling and cascading failure; minimize them, defend the one you keep.

## 3. The message path (the heart of the project)

**Transactional Outbox.**
History writes the message *and* an outbox row in one transaction; a `SKIP LOCKED` poller publishes to
Kafka and marks rows sent only after broker confirm.
*Why:* solves the dual-write problem (a crash between DB write and publish loses the event) without
distributed transactions.

**Idempotent consumer / effectively-once.**
Client-generated message UUID + `ON CONFLICT DO NOTHING`.
*Why:* at-least-once delivery + idempotent processing = effectively-once, without Kafka transactions.

**Per-conversation ordering.**
Kafka partition key = `conversationId` → one partition → one consumer (single writer) → atomic
`UPDATE…RETURNING` sequence number.
*Why:* ordering guaranteed *within* a conversation; deliberately not across conversations.

**Persist-before-fanout.**
A message fans out only after it is durable with a real seq.
*Trade-off:* one extra broker hop (~10–30 ms) and delivery availability coupled to history —
correctness over a few milliseconds.

**Broadcast fan-out (pub/sub via per-instance consumer groups).**
Each chat instance uses its own consumer group, sees every message, and delivers only to local sessions.
*Why:* the stateless alternative to sticky sessions — any instance serves any user.
*Trade-off:* O(instances × messages); scale answer = conversation→instance affinity.

## 4. Consistency & state

**CQRS-style read model.**
Chat = write/real-time path; history = derived, eventually-consistent read store rebuilt from the log.
*Why:* reads scale independently and can be replayed.

**Eventual consistency + projections.**
History keeps a membership projection from conversation events to authorize reads without a sync RPC.
*Trade-off:* a removed member may read for the propagation window — documented and acceptable.

**Watermark receipts.**
Conversation-level `last_read/delivered_seq` instead of per-message status rows; status is derived.
*Why:* avoids write amplification (N members × M messages) in groups.

**Stateless services, externalized state.**
Shared state lives in Kafka/Redis/Postgres; only the live socket registry is instance-local.
*Why:* this is what makes horizontal scaling and instance-death recovery work.

## 5. Resilience

- **Circuit breaker + retry** (Resilience4j) on the sync call and gateway routes; fail-fast + 503 fallback.
- **Dead-letter queue** with exponential backoff — poison messages isolated, pipeline keeps flowing.
- **Backpressure** — bounded producer buffers, WS send limits disconnect slow clients, consumer pause/resume.
- **Deliberate degradation** — Redis down → rate limiter *fails open* (availability over strictness);
  presence TTLs expire → shown offline. Each failure has a chosen behavior.
- **Measured breakpoint** — k6 stress found the outbox poller drain ceiling (~250 events/s) is the first
  bottleneck at ~500 VUs. A *tunable* limit (batch size / cadence / more pollers), not a fundamental one.

## 6. Cross-cutting

- **Security:** JWT at the edge + re-validation on WS CONNECT (defense in depth); rotating refresh tokens
  with reuse detection (family revocation); BCrypt; single CORS layer.
- **Observability:** Micrometer metrics (rate/errors/duration + custom e2e latency, broker lag),
  distributed tracing (Zipkin), structured logs with correlation IDs, provisioned Grafana dashboards.

---

## How to talk about it in an interview

Lead with the **problem** each pattern solves, then the **trade-off you accepted**. Interviewers care
less that you used Kafka and more that you can say:

> "Persist-before-fanout costs a broker hop but guarantees ordering before anyone sees 'delivered';
> the fan-out is O(instances × messages), and here's how I'd scale it."

Other strong soundbites:
- "Outbox turns a dual-write into a single local transaction; the poller gives at-least-once publish."
- "At-least-once + idempotent consumer = effectively-once, without Kafka transactions."
- "Broadcast consumer groups are the stateless alternative to sticky sessions."
- "Watermarks avoid per-message status write storms in groups."
- "I found the outbox drain ceiling with a load test; it's tunable, and here's the fix."

See `docs/interview-prep.md` for the 2-minute pitch and 10 likely follow-up questions.
