-- Booking Expiration feature: index supporting the safety-net scheduler query
-- (findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc) — coding-standards §Database.
CREATE INDEX idx_bookings_status_updated_at ON bookings (status, updated_at);
