-- Phase 6: tracks consumed Kafka event IDs so consumers are idempotent (EVT-005, EVT-008).
-- A unique PK on event_id prevents double-processing even under concurrent re-delivery.
CREATE TABLE consumed_events
(
    event_id    UUID                     NOT NULL,
    event_type  VARCHAR(100)             NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_consumed_events PRIMARY KEY (event_id)
);
