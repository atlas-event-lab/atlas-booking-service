package com.atlas.booking.booking.event;

import java.util.UUID;

public record FailedItem(ResourceType resourceType, UUID resourceId, int requested, int available) {}
