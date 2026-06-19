-- Cancel Booking feature:
-- cancellation_idempotency_key stores the Idempotency-Key of the cancellation request (EVT-008).
-- reason on booking_status_history persists the optional free-text reason (feature.md §Request Model).
ALTER TABLE bookings
    ADD COLUMN cancellation_idempotency_key VARCHAR(255),
    ADD CONSTRAINT uq_bookings_cancellation_idempotency_key UNIQUE (cancellation_idempotency_key);

ALTER TABLE booking_status_history
    ADD COLUMN reason VARCHAR(500);
