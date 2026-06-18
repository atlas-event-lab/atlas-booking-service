package com.atlas.booking.booking.mapper;

import com.atlas.booking.booking.dto.ApiBookingStatus;
import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.dto.MoneyResponse;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.entity.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps Booking entities to API response DTOs.
 * Internal saga states (INVENTORY_RESERVED, PAYMENT_PENDING, CANCELLING) are
 * projected onto the nearest public API status before the DTO is constructed.
 */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "status", expression = "java(toApiStatus(booking.getStatus()))")
    BookingResponse toResponse(Booking booking);

    MoneyResponse toMoneyResponse(Money money);

    /**
     * Projects internal BookingStatus onto the public API enum.
     * Internal states are hidden from callers; CANCELLING maps to CONFIRMED because
     * the cancellation is still in progress and the booking has not yet been cancelled.
     */
    default ApiBookingStatus toApiStatus(BookingStatus status) {
        return switch (status) {
            case PENDING, INVENTORY_RESERVED, PAYMENT_PENDING -> ApiBookingStatus.PENDING;
            case CONFIRMED, CANCELLING                         -> ApiBookingStatus.CONFIRMED;
            case CANCELLED                                     -> ApiBookingStatus.CANCELLED;
            case FAILED                                        -> ApiBookingStatus.FAILED;
            case EXPIRED                                       -> ApiBookingStatus.EXPIRED;
        };
    }
}
