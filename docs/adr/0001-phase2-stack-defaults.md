# ADR 0001 — Phase 2 stack & structure defaults

Status: accepted (Phase 1 approved 2026-11-24). These are the boring, interview-defensible
defaults for choices the spec left unpinned. Recorded so each is explainable in 45 minutes.

## Versions
- **Spring Boot 3.5.16** — already cached locally and validated in the UPLINK repo; avoids a fresh BOM download.
- **Spring Cloud 2025.0.3** — the release train documented compatible with Boot 3.5.x.
- **Java 21**, Maven multi-module reactor (`relay-parent`), groupId `com.relay`, working name **Relay**.

## Module layout
- `chat-events` — shared **schema-only** library: event records, enums, `Topics` constants. Zero framework deps, no business logic (per spec).
- `discovery-server` (Eureka), `gateway` (reactive Spring Cloud Gateway), `auth-service`, `chat-service`, `history-service`, `presence-service`.
- Phase 2 ships **bootable skeletons** (web + actuator + eureka + prometheus + tracing). DB/Redis/Kafka/security deps land in their owning phases (3–5) to keep each phase's gate clean and avoid premature autoconfig (e.g. Spring Security locking `/actuator/prometheus` before it's configured).

## Sync client: RestClient over OpenFeign (Phase 3+)
The single sync call (chat→auth on invite) will use Spring 6 `RestClient` + Resilience4j, not Feign. One fewer abstraction/annotation processor to explain; the spec allows "REST/OpenFeign".

## Schema provisioning
- Postgres-per-service schemas are provisioned via `ops/db/*/init.sql` mounted at `/docker-entrypoint-initdb.d` (runs on first init). Service-side migration strategy (Flyway vs `ddl-auto=validate`) is decided with the service code in Phases 3–5.
- `messages` is `PARTITION BY HASH (conversation_id)`, 8 partitions; PK/unique include the partition key as Postgres requires.

## Images & build
- One shared multi-stage `Dockerfile` with `ARG MODULE`; `mvn -pl ${MODULE} -am package` inside a BuildKit `~/.m2` cache mount. Self-contained: `docker compose up --build` needs no host Maven. Runtime `eclipse-temurin:21-jre` + `curl` for healthchecks.

## Compose topology
- `chat-service` runs **2 replicas** via `deploy.replicas` (no published host port; both register in Eureka under one app name with distinct instance IDs, and the gateway load-balances `lb:ws://chat-service` across them). This is the pinned horizontal-scaling demo.
- `gateway` runs **1 instance locally**. Non-swarm Compose cannot load-balance a *fixed published host port* across replicas (replica 2 fails with "port already allocated"). Multi-gateway HA (Phase 1 F10) is therefore **prod topology** — multiple gateway instances behind a cloud LB / DNS round-robin — not a local Compose feature. Adjusted from the Phase 1 wording; the chat tier carries the multi-instance story locally.
- **Host ports remapped to a 1xxxx range** (gateway 18080, eureka 18761, auth 18081, history 18083, presence 18084, postgres 15432-4, redis 16379, kafka 19092, zipkin 19411, prometheus 19090, grafana 13000) because this machine shares the Docker host with UPLINK and host port 5432 was allocated. Internal container ports are unchanged, so service discovery and Prometheus Docker SD are unaffected.
- Kafka: `apache/kafka:3.9.0`, single-node **KRaft**, RF=1, dual listener (INTERNAL `kafka:9092` for containers, EXTERNAL `localhost:29092` for host). `kafka-init` one-shot creates the 7 catalog topics × 12 partitions. Auto-create disabled so partition counts are deliberate.
- Redis 7, Zipkin 3, Prometheus v2.53.1, Grafana 11.1.0.

## Observability wiring
- Prometheus uses **Docker service discovery** (socket mounted read-only, container runs as root in dev) + relabel on `prometheus.scrape`/`prometheus.port` labels, so **every replica** is scraped individually (static DNS targets would only hit one).
- Grafana datasource provisioned to `http://prometheus:9090`; dashboard provider points at `ops/grafana/dashboards` (JSON added in Phase 8).
- Micrometer Tracing (Brave) → Zipkin; sampling 1.0 in dev.

## Profiles
- `local` (default): localhost infra. `docker`: container DNS (eureka/redis/zipkin/kafka), activated by `SPRING_PROFILES_ACTIVE=docker` in Compose. `prod`: env-driven placeholders (topology noted, not built).

## Known limitations (Phase 2)
- RF=1 Kafka, single Postgres per service, no TLS — local only.
- Single gateway locally (multi-gateway HA is prod topology, see above).
- Skeletons expose actuator only; no business endpoints yet (Phases 3–5).
- Prometheus runs as root to read the Docker socket (dev convenience; prod would use a socket proxy or file SD).

## Gate evidence (measured 2026-11-24)
- `mvn -DskipTests package`: BUILD SUCCESS, 7 modules, ~57s.
- `docker compose up -d`: 15 containers healthy.
- Eureka: chat-service=2, auth/gateway/history/presence=1.
- Gateway routes `/auth|/chat|/history|/presence/actuator/health` → HTTP 200 (after LB cache warmup).
- Prometheus: 7/7 active targets up (both chat replicas scraped individually).
- Grafana: Prometheus datasource provisioned, default.
- Postgres: authdb(users, refresh_tokens), chatdb(conversations, conversation_members, outbox), historydb(messages + p0..p7, conversation_state, membership_projection, read_state, outbox).
- Kafka: 7 catalog topics, 12 partitions each. Redis: PONG. Zipkin: all 5 services reported.
