-- DB-006: a table's primary key column SHALL be named `id`; prefixed PK column
-- names (booking_id, booking_item_id, …) SHALL NOT be used. Cross-aggregate
-- reference columns keep the <entity>_id form (e.g. booking_items.booking_id).
--
-- PostgreSQL preserves primary-key and foreign-key constraints across a column
-- rename, so the existing FK reference columns and their constraints are unaffected.
ALTER TABLE bookings        RENAME COLUMN booking_id      TO id;
ALTER TABLE booking_items   RENAME COLUMN booking_item_id TO id;
ALTER TABLE travelers       RENAME COLUMN traveler_id     TO id;
ALTER TABLE consumed_events RENAME COLUMN event_id        TO id;
