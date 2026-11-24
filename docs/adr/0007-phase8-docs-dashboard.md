# ADR 0007 — Phase 8 Grafana dashboard, API docs, README

Status: accepted (Phase 7 approved, Phase 8 built 2026-11-24). Completes the project.

## Grafana dashboard
- `ops/grafana/dashboards/relay.json` (uid `relay-overview`, 10 panels), auto-provisioned by the existing file provider. Datasource given a stable **uid `prometheus`** so panels bind deterministically.
- Panels: active WS connections per instance, message throughput (sent/delivered/persisted), e2e delivery latency p50/p95/p99, Kafka broker lag, rate-limit rejections, outbox pending, dedup hits, JVM heap + CPU per service, HTTP request rate.

## Aggregated API docs
- **springdoc-openapi 2.8.9**: `webmvc-ui` in auth/chat/history/presence, `webflux-ui` in the gateway.
- Gateway exposes per-service docs via `RewritePath` routes (`/auth/v3/api-docs` → auth `/v3/api-docs`, etc.), placed before the general routes; these paths + `/swagger-ui/**` + `/webjars/**` are `permitAll`. `springdoc.swagger-ui.urls` aggregates the four services into one UI at `/swagger-ui.html`.
- auth `SecurityConfig` permits `/v3/api-docs/**` + `/swagger-ui/**`; chat is already permitAll; history/presence have no security filter.

## Docs
- `README.md`: architecture Mermaid, service/data-ownership table, quick start + ports, two-tab demo, API table, **Design decisions & trade-offs**, limitations, future improvements, observability, testing, layout.
- `docs/interview-prep.md`: 2-minute pitch + 10 follow-up Q&A (boundaries, cross-instance, ordering, idempotency, persist-before-fanout, failures, outbox, offline sync, Kafka-vs-Redis, scaling) + honesty notes.
- Maven wrapper (`mvnw`) generated so `./mvnw verify` works without a host Maven.

## Gate evidence (measured 2026-11-24)
- Grafana: dashboard `relay-overview` provisioned (10 panels); datasource uid `prometheus`.
- Swagger: `/swagger-ui.html` → 200 (after redirect); `/auth|/chat|/history|/presence/v3/api-docs` → 200 OpenAPI 3.1.0 (6/5/2/2 paths).
- Panel queries return live data: `ws_connections_active` 2 series, `message_e2e_latency_seconds` 6 series, broker-lag 168 series, JVM 56 series.
- `mvn verify`: 26 tests green. README local references resolve.

## Project complete
All 8 phases delivered and committed. Known limitations and future work are in the README and ADRs.
