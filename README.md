# Atlas — Booking Service

> Owns the booking lifecycle and drives the choreographed booking saga.

Part of **[Atlas](https://github.com/atlas-event-lab)**, an event-driven distributed-systems
learning lab. See the [booking saga diagrams](https://github.com/atlas-event-lab/atlas/tree/main/diagrams/booking-saga.md).

## Responsibilities

- Create a booking, validate its price against Flight/Hotel at checkout (ADR-0001), and hold
  the single source of truth for booking state.
- Drive the saga by reacting to Inventory and Payment events; confirm, fail, expire or cancel.
- It does **not** reserve inventory or charge cards itself — those are separate services.

## Tech

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL (`booking_db`) · Kafka · Keycloak JWT.

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/bookings` | Create a booking (idempotent on client key) |
| GET | `/api/v1/bookings/{bookingId}` | Fetch a booking |
| POST | `/api/v1/bookings/{bookingId}/cancellation` | Cancel a `CONFIRMED` booking |

Booking also reads Flight and Hotel price endpoints to validate totals (ADR-0001, ADR-0004/0005).

## Events

**Produces:** `booking.created`, `booking.confirmed`, `booking.cancelled`, `booking.failed`,
`booking.expired`.

**Consumes:** `inventory.reserved`, `inventory.rejected`, `inventory.released`,
`payment.succeeded`, `payment.failed`, `payment.timed_out`.

## State machine

`PENDING → INVENTORY_RESERVED → CONFIRMED`, with `FAILED` / `EXPIRED` branches and
`CONFIRMED → CANCELLING → CANCELLED`. Out-of-order payment events arriving in `PENDING` are
**deferred and retried** (ADR-0007). Full diagram in the hub repo.

## Data

Owns `booking_db` (database-per-service). No cross-service DB access.

## Patterns

Transactional outbox · idempotent consumers (`ConsumedEvent`) · state-transition guard ·
scheduled expiration safety-net · per-consumer DLT topics (ADR-0024).

## Run locally

```bash
docker compose up booking-service     # from the hub repo, brings up deps too
# or
./gradlew bootRun
```

Env: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_ISSUER_URI`.

## License

Apache-2.0 — see [`LICENSE`](./LICENSE).
