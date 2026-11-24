# Relay k6 load test — results

## How to run

```bash
# from the repo root, with the stack up (docker compose up -d --build)
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:19090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="avg,med,p(95),p(99),max" \
  k6 run --out experimental-prometheus-rw --summary-export=loadtest/summary.json loadtest/relay-load.js
```

Metrics stream to Prometheus (remote-write receiver enabled) and render on the Grafana
dashboard **"Relay — k6 Load Test"** (`relay-k6`). The server-side effect is visible on
"Relay — Distributed Chat Overview" (connections, throughput, e2e latency, broker lag).

## What it does

- Hand-rolled **STOMP over `k6/ws`** through the **gateway** (k6 has no STOMP client). The gateway
  round-robins WS connections across chat replicas, so load spreads across instances.
- **Register once per VU** (a user registers once and holds a connection), then opens a 1:1 with a
  shared peer and sends a message every 2s, measuring **send→echo delivery latency**.
  Re-registering every iteration would make the load generator's BCrypt hashing the CPU bottleneck
  (the UPLINK lesson) — avoided here.
- Ramp: 0→50→100→150 VUs over ~60s. Thresholds: `ws_errors rate<0.05`, `delivery_latency p95<2000ms`.

## Measured (2026-11-24, 150 VUs, single shared Docker host)

| Metric | Run 1 | Run 2 |
|---|---|---|
| VUs max | 150 | 150 |
| Iterations complete | 146 | 146 |
| WS upgrade checks | 100% | 100% |
| **Delivery latency avg** | 174 ms | 134 ms |
| **Delivery latency p95** | 286 ms | 229 ms |
| **Delivery latency p99** | 354 ms | 243 ms |
| Delivery latency max | 614 ms | 293 ms |
| Messages sent | 2473 (37/s) | 2558 (39/s) |
| HTTP req duration p95 | 116 ms | 86 ms |
| HTTP req failed | 0% | 0% |
| **ws_errors** | 0% | 0% |

Both runs **passed all thresholds**. At 150 VUs the load generator was *not* the bottleneck
(register-once-per-VU + modest ramp); delivery p95 stayed well under the 2s threshold.

## Honesty notes

- These numbers are from a **single shared Docker host** (services + Kafka + Postgres + the k6
  generator all on one machine), so they reflect a constrained environment, not a production ceiling.
- The server-only ceiling was **not** measured (would need a separate load generator host).
- 126 iterations were interrupted by the ramp-down (expected); 146 completed cleanly.
- `summary.json` holds the raw k6 summary; `k6-run.log` the console output.

## Stress / breakpoint test (`relay-stress.js`, ramp to 600 VUs)

Ran a higher ramp (0→200→400→600 VUs) to find where it breaks. **It breaks on delivery latency,
not on connections or REST.**

| Signal @ 600 VUs | Value |
|---|---|
| **Delivery latency p95** | **11.65 s** (threshold 2 s → **FAILED**) |
| Delivery latency avg / med / p99 | 3.44 s / 2.17 s / 13.33 s |
| ws_errors / http_req_failed / upgrade checks | 0% / 0% / 100% |
| HTTP req duration p95 | 121 ms (healthy) |
| ws_msgs sent / received | 193/s / 129/s (backlog) |
| **history outbox_pending peak** | **3175 rows** |
| Kafka command-consumer lag peak | 0 |
| history persisted rate peak | ~254/s |

**Bottleneck attribution (measured, not guessed):**
- The **history outbox poller** drains `batch 50 / fixedDelay 200ms` ≈ **250 events/s**. Persisted
  rate capped at ~254/s while offered load at 600 VUs ≈ 300 msg/s (600 × 0.5/s). Offered > drain →
  outbox backlog grew to 3175 → delivery latency climbed past the threshold.
- Kafka **command consumption kept up (lag 0)** — the bottleneck is the *outbox publish* side, not
  the log or the consumer.
- The **load generator was not the bottleneck**: host memory stayed ~660–740 MiB free, service CPU
  low (chat ~2%, Kafka ~2%). Connections and REST were healthy.
- Breakpoint is therefore ~**500 VUs** (≈250 msg/s offered), where offered rate crosses the outbox
  drain ceiling. Below that (150 VUs) delivery p95 was ~229 ms.

**This is a tunable ceiling, not a fundamental limit:** raise the poller batch size / lower the
delay, run multiple poller instances (the `SKIP LOCKED` claim already supports it), or batch Kafka
publishes. That is the first thing to scale for higher write throughput.

> Host caveat: 8-CPU / 6.5 GiB shared box; a memory guard aborted nothing here ( stayed > 600 MiB
> free). The server-only ceiling on dedicated hardware was not measured.
