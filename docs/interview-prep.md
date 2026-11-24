# Interview prep — Relay

## 2-minute pitch

> "Relay is a distributed real-time chat system I built to demonstrate microservice design and
> distributed-systems thinking, not just CRUD. It's Java 21 / Spring Boot 3.5 with Spring Cloud
> Gateway as the single edge — JWT validation, per-user Redis rate limiting, CORS, and circuit
> breakers — and Eureka for discovery.
>
> I split it into four business services with clear data ownership: auth, a stateless chat relay
> that owns conversations, a history service that is the system of record for messages, and a
> presence service backed by Redis. They communicate asynchronously through Kafka.
>
> The interesting part is the message path. A send is published to a Kafka topic partitioned by
> conversation ID. The history service — the single writer per partition — dedups by client message
> ID, assigns a monotonic sequence number atomically, and writes the message plus an outbox row in
> one transaction. An outbox poller publishes a 'persisted' event, and every chat instance consumes
> it with its own consumer group and pushes to whichever members have a local WebSocket. That gives
> me per-conversation ordering, idempotent retries, and cross-instance fan-out without sticky
> sessions. Offline users gap-sync from history by sequence number on reconnect.
>
> I hardened it with a transactional outbox, Kafka dead-letter queues, Resilience4j retry + circuit
> breaker on the one synchronous call, and full observability — Prometheus metrics including
> end-to-end delivery latency and broker lag, a provisioned Grafana dashboard, and Zipkin tracing.
> Every phase was verified with real measured runs, and the trade-offs are documented in ADRs."

## 10 likely follow-ups

**1. Why these service boundaries? Why not merge history into chat?**
Chat is the stateful real-time edge and owns the conversation aggregate so membership checks are a
local indexed lookup at line rate. History is a separate *read model* (CQRS): it can lag by
milliseconds, be rebuilt from the log, and use a different partition/index layout. Merging them would
couple the low-latency fan-out path to pagination/replay workloads and violate database-per-service.

**2. How do users on different chat instances message each other?**
Broadcast fan-out: each instance consumes the persisted topic with its **own consumer group**, so
every instance sees every message and delivers only to members with a local session
(`SessionRegistry`). No routing-by-user, no sticky sessions — any instance serves any user. The
trade-off is O(instances × messages); at scale I'd shard by conversation→instance affinity.

**3. How is per-conversation ordering guaranteed?**
Kafka partition key = conversationId, so all events for a conversation land on one partition, consumed
by one history instance (single writer). Seq is assigned with atomic `UPDATE … RETURNING`. Clients
render by seq and resync on gaps. Cross-conversation ordering is intentionally not guaranteed.

**4. How do you handle duplicate sends and retries?**
The client generates a message UUID. History dedups with `ON CONFLICT (conversation_id,
client_message_id) DO NOTHING` and a dedup counter metric. At-least-once Kafka delivery + idempotent
consumer = effectively-once, without Kafka transactions.

**5. Why persist-before-fanout? What's the cost?**
A message fans out only after history persists it and the outbox publishes the persisted event, so
ordering and durability are guaranteed before anyone sees "delivered", and the seq is real. Cost: one
extra broker hop (~10–30 ms) and fan-out availability is coupled to history. I chose correctness over
the few-ms saving of fanout-before-persist.

**6. What happens when history / Kafka / Redis is down?**
History down: commands accumulate in Kafka (lag metric climbs), clients show "sent" not "delivered",
and on recovery the consumer catches up in order. Kafka down: producers fail fast → NACK, client
holds and retries with the same ID. Redis down: rate limiter fails open (availability over
strictness), presence TTLs expire → shown offline, rebuilt by heartbeats. Each is a documented,
deliberate degradation.

**7. Why an outbox instead of publishing right after the DB write?**
"Write DB then publish" can lose the event if the process crashes between the two. The outbox makes
the event part of the same atomic commit; a `SKIP LOCKED` poller publishes and only marks rows sent
after a broker confirm. That's reliable event publishing without distributed transactions.

**8. How does offline sync work? Why not Kafka replay for clients?**
On reconnect the client calls history with `afterSeq = lastSeen` and gets the gap in order (keyset
pagination). Clients don't read Kafka: the log's retention/offsets are an internal concern, and the
read model is the right source for user-facing history. Kafka replay is used to *rebuild the read
model*, not to serve clients.

**9. Why Kafka over Redis Pub/Sub?**
Ordering (partition key), durable replay (offline sync + read-model rebuild), consumer groups, DLQ,
and lag metrics are native to Kafka and would need bespoke machinery on Redis Pub/Sub, which is
fire-and-forget. Redis stays for presence and rate limiting — the ephemeral jobs it's best at.

**10. How would you scale this further? What breaks first?**
First the broadcast fan-out (O(instances × messages)) — I'd add conversation→instance affinity or a
last-mile pub/sub. Then Kafka RF=3 and Postgres read replicas / time-range partitioning for cold data,
pgBouncer, Redis Cluster, and Kubernetes with Spring Cloud Kubernetes discovery. Multi-region would
need MirrorMaker2 and conflict-free read-state (CRDTs).

## Honesty notes

- Single broker/DB/gateway locally; RF=1. Prod topology is documented, not built.
- HS256 shared secret; RS256/JWKS is the stated evolution.
- Every claim above maps to code and a measured gate in the ADRs — don't claim numbers you didn't run.
