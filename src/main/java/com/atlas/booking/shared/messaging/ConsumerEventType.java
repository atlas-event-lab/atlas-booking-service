package com.atlas.booking.shared.messaging;

public enum ConsumerEventType {
    INVENTORY_RESERVED,
    INVENTORY_REJECTED,
    INVENTORY_RELEASED,

    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_TIMEOUT
}
