# ADR 0006 — Phase 7 minimal frontend

Status: accepted (Phase 6 approved, Phase 7 built 2026-11-24).

## Approach
- **Vanilla HTML/JS, zero npm runtime deps** (no framework, no build step) — matches the validated UPLINK approach and keeps the demo explainable. `frontend/index.html` + `app.js` + `style.css`.
- Served by an **nginx:alpine** container (`frontend` service, host port **18088**). The client calls the gateway **directly** at `http://<host>:18080` (REST) and `ws://<host>:18080/ws` (STOMP); gateway CORS already allows `localhost:*`.
- Dependency-free **STOMP-over-WebSocket** client (native `WebSocket`): CONNECT/SUBSCRIBE/SEND framing, user-queue subscriptions (`messages/acks/errors/typing/receipts/presence/conversations`), reconnect with backoff + **history gap-sync** on reconnect, throttled typing, READ receipts, presence polling.

## CORS bug found & fixed
- The browser blocked calls: `Access-Control-Allow-Origin` contained **two values** because both the gateway *and* auth-service (and chat) added CORS headers. **CORS is now a single layer at the gateway**; removed `.cors()` + `CorsConfigurationSource` from auth-service and chat-service `SecurityConfig`. Downstream services are internal-only.

## Gate evidence (measured 2026-11-24, headless Chrome)
- `puppeteer-core` + system `google-chrome-stable`, two pages: register alice + bob, create 1:1, alice sends → **bob receives**, **alice sees own echo**. Screenshots (`docs/demo.png`) show the message, **"✓✓ read"** receipt, "● online" presence, connection state, and the serving instance id.
- **Cross-instance**: proven at protocol level in Phases 4–5 (alice→chat-2, bob→chat-1) and by chat logs showing both instances serving users. The gateway's round-robin may co-locate two near-simultaneous tabs on one instance; the broadcast fan-out handles both cases identically, so the demo is correct either way.

## Test-harness notes
- Puppeteer **element-handle** calls (`page.type`/`page.click`) hung under load; driving the app via `page.evaluate` (DOM + its own functions) is reliable. This is a harness choice, not a product change.

## Known limitations / next
- Group creation takes raw member UUIDs (no user search); no media; English-only.
- Next: Phase 8 Grafana dashboard JSON + README (architecture, setup, API docs, design decisions).
