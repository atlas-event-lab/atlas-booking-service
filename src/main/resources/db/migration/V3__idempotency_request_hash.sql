-- Phase 4: store a SHA-256 hash of the original request body so the
-- idempotency check can detect same-key / different-payload replays (EVT-008).
ALTER TABLE bookings ADD COLUMN request_hash VARCHAR(64);
