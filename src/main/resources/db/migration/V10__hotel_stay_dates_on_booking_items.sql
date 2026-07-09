-- ADR-0010: hotel stay dates on booking items. `booking_items` becomes a SINGLE_TABLE hierarchy
-- (FlightBookingItem / HotelBookingItem) discriminated by the existing `type` column; hotel items add
-- nullable check_in / check_out. Forward-only, additive DDL.
ALTER TABLE booking_items ADD COLUMN check_in  DATE;
ALTER TABLE booking_items ADD COLUMN check_out DATE;
