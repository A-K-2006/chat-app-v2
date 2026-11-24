# ADR 0002 — Phase 3 auth-service + gateway edge

Status: accepted (Phase 2 approved, Phase 3 built 2026-11-24).

## Auth tokens
- **HS256 self-issued JWT** via Spring Security `NimbusJwtEncoder`/`NimbusJwtDecoder` (shared secret from `JWT_SECRET` env). Access TTL 15m, issuer `relay-auth`, claims `sub`=userId + `username`. Same trust domain as Phase 1 A5; RS256/JWKS is the evolution.
- **Refresh tokens**: opaque 256-bit random (URL-safe base64), stored **SHA-256 hashed**, TTL 14d, rotating with `family_id` lineage + `rotated_from`.
- **Reuse detection**: presenting a revoked token revokes the whole family. The family revoke runs in `@Transactional(REQUIRES_NEW)` on the repository method so it **commits even though the request is rejected** (the caller's transaction rolls back). This was caught by the Testcontainers integration test — a real bug, not a loosened assertion.
- Passwords: **BCrypt** (default strength 10), 8–72 chars (BCrypt 72-byte limit).

## Gateway edge (reactive)
- `spring-boot-starter-oauth2-resource-server` + `NimbusReactiveJwtDecoder` (HS256). `permitAll`: `/auth/**`, `/actuator/**`, `/fallback`, OPTIONS. Everything else authenticated.
- **WS handshake auth**: browsers can't set `Authorization` on an upgrade, so `WsTokenWebFilter` (`@Order(-200)`, before security) promotes `?access_token=` to a Bearer header for `/ws/**`. Chat-service will re-validate on STOMP CONNECT (Phase 4, defense in depth).
- **User context**: `UserContextGlobalFilter` reads the authenticated `Jwt` and forwards `X-User-Id`/`X-Username` downstream.
- **Rate limiting**: Redis `RequestRateLimiter` (replenish 20/s, burst 40), `KeyResolver` = JWT subject, falling back to client IP for public routes. Fail-open if Redis is down (availability over strictness; Redis health indicator disabled at the edge).
- **CORS**: `CorsConfigurationSource` bean, allowed origin patterns `http://localhost:*` / `127.0.0.1:*`, credentials true.
- **Circuit breakers**: Resilience4j per route (`authCB`/`chatCB`/`historyCB`/`presenceCB`), 2s time limiter, fallback `forward:/fallback` → 503 JSON.

## Spring Cloud 2025.0.x / Gateway 4.3.x specifics (gotchas hit)
- The reactive gateway needs **`spring-cloud-starter-circuitbreaker-reactor-resilience4j`**, not the servlet `...-resilience4j` starter — otherwise the `CircuitBreaker` GatewayFilterFactory is absent and the gateway crashes on route refresh.
- Gateway config namespace moved to **`spring.cloud.gateway.server.webflux.*`** (routes/discovery). The old `spring.cloud.gateway.*` keys are auto-relocated but deprecated; we use the new namespace.

## Routing scheme (changed from Phase 2)
Full-path routing, **no StripPrefix**: `/auth/**`,`/users/**`→auth; `/chat/**`,`/conversations/**`→chat; `/ws/**`→`lb:ws://chat-service`; `/history/**`→history; `/presence/**`→presence. Phase 2's `/auth/actuator/health` proof is superseded by real endpoint flows. Prometheus still scrapes each service's `/actuator/prometheus` directly by container IP (unaffected).

## Persistence & trust
- auth-service uses `ddl-auto=none` against the init-SQL-provisioned `authdb` (Phase 2). Flyway is the evolution if the schema changes.
- `/internal/users/{id}` is `permitAll` but **not routed through the gateway** — reachable only on the Docker network (service-to-service trust). Consumed by chat-service on invite (Phase 4) via RestClient + Resilience4j.

## Tests (13 green)
- auth-service (10): `TokenServiceTest` (3), `AuthServiceTest` Mockito (4, incl. reuse + expired), `AuthFlowIntegrationTest` Testcontainers Postgres (3, full HTTP lifecycle + family revocation + logout + internal lookup).
- gateway (3): `WsTokenWebFilterTest` (query-param promotion, non-WS untouched, no override).

## Gate evidence (measured 2026-11-24, through gateway :18080)
- `mvn verify`: BUILD SUCCESS, 13 tests.
- register→201; `/users/me` no-token/garbage→401, valid→200; login good→200/bad→401.
- refresh rotate→200; reuse old→401; family-revoked rotated→401; logout→204; refresh-after→401.
- CORS preflight→`Access-Control-Allow-Origin: http://localhost:5173`.
- Rate limit: 80 concurrent→60×401 + 20×429; Redis `request_rate_limiter.{auth.<ip>}.tokens/.timestamp` present.
- Circuit breaker: auth stopped→20×503 fallback JSON; auth restarted→route recovered (401), CB closed.
- Prometheus 7/7 targets up; all containers healthy.

## Known limitations / next
- HS256 shared secret (no JWKS/rotation); dev secret in Compose (prod must override).
- Single gateway locally; rate-limit fail-open; internal trust by network only.
- No refresh-token absolute session cap / device management yet.
- Next: Phase 4 chat-service (STOMP, Kafka send path, conversation aggregate, RestClient→auth invite check).
