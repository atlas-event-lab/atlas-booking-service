-- Phase 5: Transactional Outbox (EVT-009).
-- Events are written here in the SAME transaction as the booking state change;
-- the OutboxRelay scheduled publisher reads PENDING/FAILED rows and publishes them
-- to Kafka, then marks them PUBLISHED. This removes the dual-write between the
-- database and Kafka (coding-standards §Outbox & Event Publishing).
CREATE TABLE outbox
(
    id             UUID                     NOT NULL,
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    event_version  INTEGER                  NOT NULL,
    payload        JSONB                    NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE,
    attempts       INTEGER                  NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

-- Relay polls unpublished rows oldest-first
-- (coding-standards §Indexes: outbox by status, created_at).
CREATE INDEX idx_outbox_status_created_at ON outbox (status, created_at);
