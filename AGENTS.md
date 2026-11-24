You are a senior backend engineer and system design mentor. Help me build a scalable, distributed real-time chat application using Java 21 and Spring Boot 3.x with a microservices architecture. It is an internship project showcase, so the design should be defensible in interviews, not just functional.

## Goals
- Support horizontal scaling: multiple instances of each service behind Spring Cloud Gateway, where users connected to different chat-service instances can still message each other.
- Demonstrate real distributed-systems thinking: service boundaries, message ordering, delivery guarantees, idempotency, failure handling, and backpressure.
- Be runnable locally with a single `docker compose up`, and be deployable to the cloud.

## Functional requirements
1. Auth: register/login with JWT access + refresh tokens (Spring Security).
2. 1:1 chats and group chats (create group, add/remove members).
3. Real-time messaging over WebSocket (STOMP or raw WebSocket; justify the choice).
4. Message history with cursor-based pagination.
5. Presence (online/offline) and typing indicators.
6. Delivery status: sent, delivered, read.
7. Offline users receive undelivered messages when they reconnect.
8. Basic rate limiting per user.

## Architecture requirements
- Microservices only. Do not propose a monolith or a modular monolith. Choose a small, well-justified set of services (for example: auth/user service, chat/websocket service, message-history service, presence service, notification service) and explain each service's responsibility and data ownership. Don't over-split; each service must have a clear reason to exist.
- Spring Cloud Gateway as the single entry point: routing, JWT validation at the edge, WebSocket route support, per-user rate limiting (Redis-backed RequestRateLimiter), CORS, circuit breakers (Resilience4j), and load balancing across service instances.
- Service discovery: use Eureka or Spring Cloud Kubernetes (or Docker DNS if simpler), and justify the choice. Centralized config via Spring Cloud Config or environment variables, with justification.
- Stateless service instances. All shared state lives in external systems.
- Database per service. No shared tables between services.
- Inter-service communication: asynchronous events through the broker for fan-out and decoupling, and synchronous REST/OpenFeign only where truly needed, with timeouts, retries, and fallbacks.
- Cross-instance message fan-out using a broker (Kafka or Redis Pub/Sub; compare both briefly and pick one with reasons).
- Redis for presence, session-to-instance mapping, and rate limiting.
- Persistent storage: PostgreSQL for users/groups/metadata, and a storage choice for messages (PostgreSQL with partitioning, MongoDB, or Cassandra; justify it).
- Idempotent message sends (client-generated message ID) to handle retries and duplicates.
- Per-conversation ordering strategy (for example, Kafka partition key by conversationId, or sequence numbers).
- Handle WebSocket connections through the gateway (sticky sessions or a stateless alternative) and explain how reconnects work when an instance dies.
- Handle distributed-systems concerns explicitly: partial failures, eventual consistency, outbox pattern or equivalent for reliable event publishing, dead-letter queues, and distributed tracing.

## Non-functional requirements
- Clean layered structure in each service (controller, service, repository, domain, dto), with validation and global exception handling. Use a shared library module only for truly common code (event schemas, common DTOs), not business logic.
- Multi-module Maven or Gradle build, with config via environment variables and Spring profiles (local, docker, prod).
- Observability: Spring Actuator, Micrometer + Prometheus metrics, structured logging with correlation IDs propagated across services, and distributed tracing (Micrometer Tracing with Zipkin or OpenTelemetry). Expose custom metrics such as active WebSocket connections per instance, messages sent/delivered per second, end-to-end delivery latency, broker lag, and rate-limit rejections.
- Grafana dashboard (provisioned as JSON) showing JVM metrics per service, active connections per instance, message throughput, delivery latency percentiles, and broker lag.
- Tests: unit tests, plus integration tests using Testcontainers (Postgres, Redis, Kafka) and contract tests between services (Spring Cloud Contract or Pact).
- Security: password hashing, JWT validation at the gateway and on the WebSocket handshake, service-to-service trust (internal network or propagated token), input sanitization, CORS config.

## Deliverables (do these in order, and stop after each phase for my confirmation)
Phase 1: Architecture proposal. Include a component diagram (Mermaid), the list of microservices with responsibilities and owned data, tech choices with trade-offs, data models/schemas per service, the event catalog (topics, payloads, keys), message flow for sending a message across two chat-service instances through the gateway, and a list of failure scenarios with how each is handled.
Phase 2: Multi-module project structure and Docker Compose (Spring Cloud Gateway, service instances, discovery server, Postgres per service, Redis, Kafka, Zipkin, Prometheus, Grafana with provisioned datasource).
Phase 3: Spring Cloud Gateway and the auth/user service with full code.
Phase 4: Core chat service (WebSocket config, handlers, broker integration, persistence) with full code.
Phase 5: Message-history service, presence service, typing, delivery/read receipts, offline message sync.
Phase 6: Resilience (circuit breakers, retries, outbox, DLQ), custom Micrometer metrics, and tests.
Phase 7: A minimal frontend (simple HTML/JS or React) to demo chat between two browser tabs connected to different chat-service instances through the gateway.
Phase 8: Grafana dashboard JSON and a README with architecture diagram, setup steps, API docs (OpenAPI/Swagger aggregated at the gateway), and a "Design decisions & trade-offs" section.

## Output rules
- Give complete, compilable code with file paths. No pseudocode or "implement this yourself" placeholders.
- Use current best practices (Spring Boot 3, Spring Cloud 2023.x or newer compatible release, Jakarta packages, records for DTOs where suitable, constructor injection, no deprecated APIs).
- Explain each important design decision in 2-3 lines, focused on the "why".
- At the end of each phase, list known limitations and what could be improved next.
- If a requirement is ambiguous, state your assumption and continue instead of asking many questions.

## Showcase extras (at the end)
- A "How to explain this project in an interview" cheat sheet: 2-minute pitch, 10 likely follow-up questions on microservice boundaries, scaling, ordering, consistency, and failures, with strong answers.
- A list of future improvements (sharding, E2E encryption, media messages, multi-region, Kubernetes deployment).

Start with Phase 1 only.
