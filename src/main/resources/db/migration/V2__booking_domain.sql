-- Booking domain tables: Booking aggregate root, BookingItem, Traveler, BookingStatusHistory.

CREATE TABLE bookings
(
    booking_id      UUID                     NOT NULL,
    user_id         UUID                     NOT NULL,
    trip_id         UUID                     NOT NULL,
    status          VARCHAR(50)              NOT NULL,
    payment_id      UUID,
    currency        VARCHAR(3)               NOT NULL,
    total_amount    NUMERIC(19, 2)           NOT NULL,
    correlation_id  VARCHAR(36)              NOT NULL,
    saga_id         UUID                     NOT NULL,
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at    TIMESTAMP WITH TIME ZONE,
    cancelled_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_bookings PRIMARY KEY (booking_id),
    CONSTRAINT uq_bookings_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE booking_items
(
    booking_item_id UUID           NOT NULL,
    booking_id      UUID           NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    resource_id     UUID           NOT NULL,
    quantity        INTEGER        NOT NULL,
    unit_price      NUMERIC(19, 2) NOT NULL,
    subtotal        NUMERIC(19, 2) NOT NULL,
    CONSTRAINT pk_booking_items PRIMARY KEY (booking_item_id),
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
);

CREATE TABLE travelers
(
    traveler_id     UUID                     NOT NULL,
    booking_id      UUID                     NOT NULL,
    first_name      VARCHAR(100)             NOT NULL,
    last_name       VARCHAR(100)             NOT NULL,
    date_of_birth   DATE                     NOT NULL,
    nationality     VARCHAR(3)               NOT NULL,
    document_type   VARCHAR(50),
    document_number VARCHAR(100)             NOT NULL,
    email           VARCHAR(255),
    phone_number    VARCHAR(50),
    CONSTRAINT pk_travelers PRIMARY KEY (traveler_id),
    CONSTRAINT fk_travelers_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
);

CREATE TABLE booking_status_history
(
    id               UUID                     NOT NULL,
    booking_id       UUID                     NOT NULL,
    previous_status  VARCHAR(50),
    new_status       VARCHAR(50)              NOT NULL,
    transitioned_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_booking_status_history PRIMARY KEY (id),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
);

-- Indexes: foreign keys + correlationId per coding standards.
CREATE INDEX idx_bookings_correlation_id ON bookings (correlation_id);
CREATE INDEX idx_booking_items_booking_id ON booking_items (booking_id);
CREATE INDEX idx_travelers_booking_id ON travelers (booking_id);
CREATE INDEX idx_booking_status_history_booking_id ON booking_status_history (booking_id);
