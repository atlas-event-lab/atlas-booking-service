package com.atlas.booking.booking.service;

import com.atlas.booking.booking.client.ExchangeRateClient;
import com.atlas.booking.booking.client.FlightPriceClient;
import com.atlas.booking.booking.client.HotelPriceClient;
import com.atlas.booking.booking.client.dto.ExchangeRateDto;
import com.atlas.booking.booking.client.dto.FlightPriceResponse;
import com.atlas.booking.booking.client.dto.MoneyDto;
import com.atlas.booking.booking.client.dto.RoomTypePriceResponse;
import com.atlas.booking.booking.dto.BookingItemSelectionRequest;
import com.atlas.booking.booking.dto.BookingResponse;
import com.atlas.booking.booking.dto.CancelBookingRequest;
import com.atlas.booking.booking.dto.CreateBookingRequest;
import com.atlas.booking.booking.entity.Booking;
import com.atlas.booking.booking.entity.BookingItem;
import com.atlas.booking.booking.entity.BookingItemType;
import com.atlas.booking.booking.entity.BookingStatus;
import com.atlas.booking.booking.entity.BookingStatusHistory;
import com.atlas.booking.booking.entity.ConsumedEvent;
import com.atlas.booking.booking.entity.Money;
import com.atlas.booking.booking.entity.Traveler;
import com.atlas.booking.booking.event.BookingCreatedPayload;
import com.atlas.booking.booking.event.BookingItemEvent;
import com.atlas.booking.booking.event.BookingLifecyclePayload;
import com.atlas.booking.booking.event.MoneyEvent;
import com.atlas.booking.booking.exception.BookingAccessDeniedException;
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.BookingNotCancellableException;
import com.atlas.booking.booking.exception.CatalogUnavailableException;
import com.atlas.booking.booking.exception.CatalogValidationException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.mapper.BookingMapper;
import com.atlas.booking.booking.messaging.OutboxEventWriter;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.repository.ConsumedEventRepository;
import com.atlas.booking.shared.messaging.EventType;
import com.atlas.booking.shared.messaging.ConsumerEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Booking Service implementation.
 * Handles pricing validation via Flight/Hotel Services, idempotency, state-transition
 * guarding, Booking persistence, and Saga choreography participation.
 * All entity-to-DTO mapping is performed here before returning to callers
 * (coding-standards §Layer Responsibilities).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CURRENCY_USD = "USD";
    private static final int CONVERSION_SCALE = 6;

    private final BookingRepository bookingRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final FlightPriceClient flightPriceClient;
    private final HotelPriceClient hotelPriceClient;
    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final BookingMapper bookingMapper;
    private final ExchangeRateClient exchangeRateClient;


    // -------------------------------------------------------------------------
    // REST handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BookingCreationResult createBooking(String idempotencyKey, CreateBookingRequest request) {
        String incomingHash = hashRequest(request);

        // Idempotency check (EVT-008)
        var existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Booking existingBooking = existing.get();
            if (!incomingHash.equals(existingBooking.getRequestHash())) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            log.info("Idempotent replay: bookingId={}, idempotencyKey={}",
                    existingBooking.getBookingId(), idempotencyKey);
            return new BookingCreationResult(bookingMapper.toResponse(existingBooking), true);
        }

        UUID userId = extractUserId();

        // Validate each item against its catalog service and compute total server-side
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (BookingItemSelectionRequest item : request.items()) {
            validateCatalogPrice(item);

            BigDecimal lineTotal = getUSDPrice(item)
                    .multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_EVEN);
            totalAmount = totalAmount.add(lineTotal);
        }

        Money total = new Money(totalAmount, CURRENCY_USD);

        validateTotalAmount(request.total().amount(), totalAmount);

        UUID bookingId    = UUID.randomUUID();
        UUID sagaId       = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Booking booking = new Booking(
            bookingId,
            userId,
            BookingStatus.PENDING,
            total,
            correlationId,
            sagaId,
            idempotencyKey,
            incomingHash
        );

       request.items().forEach( item -> {
            BigDecimal lineTotal = getUSDPrice(item)
                    .multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_EVEN);

            booking.addItem(new BookingItem(
                    UUID.randomUUID(),
                    item.type(),
                    item.resourceId(),
                    item.quantity(),
                    item.unitPrice().amount(),
                    lineTotal
            ));
        });

       request.travelers().forEach( travelerRequest ->
            booking.addTraveler(new Traveler(
                    UUID.randomUUID(),
                    travelerRequest.firstName(),
                    travelerRequest.lastName(),
                    travelerRequest.dateOfBirth(),
                    travelerRequest.nationality(),
                    travelerRequest.documentType(),
                    travelerRequest.documentNumber(),
                    travelerRequest.email(),
                    travelerRequest.phoneNumber()))
       );

        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), null, BookingStatus.PENDING));

        Booking saved = bookingRepository.save(booking);

        // Write BookingCreated to the outbox in this same transaction (EVT-009).
        outboxEventWriter.write(
            saved.getBookingId(),
            EventType.BOOKING_CREATED,
            saved.getCorrelationId(),
            saved.getSagaId().toString(),
            buildCreatedPayload(saved));

        log.info("Booking created: bookingId={}, userId={}, sagaId={}, correlationId={}",
                bookingId, userId, sagaId, correlationId);

        return new BookingCreationResult(bookingMapper.toResponse(saved), false);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID bookingId) {
        return bookingMapper.toResponse(
                bookingRepository.findById(bookingId)
                        .orElseThrow(() -> new BookingNotFoundException(bookingId)));
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BookingResponse cancelBooking(String idempotencyKey, UUID bookingId, CancelBookingRequest request) {
        // Idempotency check (EVT-008)
        var existing = bookingRepository.findByCancellationIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Booking existingBooking = existing.get();
            if (!existingBooking.getBookingId().equals(bookingId)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            log.info("Idempotent cancel replay: bookingId={}, idempotencyKey={}", bookingId, idempotencyKey);
            return bookingMapper.toResponse(existingBooking);
        }

        UUID userId = extractUserId();
        Booking booking = findBooking(bookingId);

        // Ownership check (SEC-004)
        if (!booking.getUserId().equals(userId)) {
            throw new BookingAccessDeniedException(bookingId);
        }

        BookingStatus from = booking.getStatus();
        if (from != BookingStatus.CONFIRMED) {
            throw new BookingNotCancellableException(bookingId, from);
        }
        StateTransitionGuard.assertAllowed(from, BookingStatus.CANCELLING);

        booking.setStatus(BookingStatus.CANCELLING);
        booking.setCancellationIdempotencyKey(idempotencyKey);

        String reason = (request != null) ? request.reason() : null;
        booking.addStatusHistory(
                new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.CANCELLING, reason));

        // Write BookingCancelled to the outbox in this same transaction (EVT-009).
        // The terminal CANCELLED transition (and cancelledAt) is applied later on InventoryReleased.
        publishLifecycle(booking, EventType.BOOKING_CANCELLED);

        log.info("Booking cancellation initiated: bookingId={}, userId={}, from={}, to=CANCELLING",
                bookingId, userId, from);

        return bookingMapper.toResponse(booking);
    }

    // -------------------------------------------------------------------------
    // Saga choreography handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void onInventoryReserved(UUID eventId, UUID bookingId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate InventoryReserved: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();
        StateTransitionGuard.assertAllowed(from, BookingStatus.INVENTORY_RESERVED);

        booking.setStatus(BookingStatus.INVENTORY_RESERVED);
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.INVENTORY_RESERVED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.INVENTORY_RESERVED));

        log.info("Booking transitioned to INVENTORY_RESERVED: bookingId={}", bookingId);
    }

    @Override
    @Transactional
    public void onInventoryRejected(UUID eventId, UUID bookingId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate InventoryRejected: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();
        StateTransitionGuard.assertAllowed(from, BookingStatus.FAILED);

        booking.setStatus(BookingStatus.FAILED);
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.FAILED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.INVENTORY_REJECTED));
        publishLifecycle(booking, EventType.BOOKING_FAILED);

        log.info("Booking transitioned to FAILED (InventoryRejected): bookingId={}", bookingId);
    }

    @Override
    @Transactional
    public void onPaymentSucceeded(UUID eventId, UUID bookingId, UUID paymentId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate PaymentSucceeded: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();
        StateTransitionGuard.assertPaymentTransition(from, BookingStatus.CONFIRMED);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(paymentId);
        booking.setConfirmedAt(Instant.now());
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.CONFIRMED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.PAYMENT_SUCCEEDED));
        publishLifecycle(booking, EventType.BOOKING_CONFIRMED);

        log.info("Booking transitioned to CONFIRMED: bookingId={}, paymentId={}", bookingId, paymentId);
    }

    @Override
    @Transactional
    public void onPaymentFailed(UUID eventId, UUID bookingId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate PaymentFailed: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();
        StateTransitionGuard.assertPaymentTransition(from, BookingStatus.FAILED);

        booking.setStatus(BookingStatus.FAILED);
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.FAILED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.PAYMENT_FAILED));
        publishLifecycle(booking, EventType.BOOKING_FAILED);

        log.info("Booking transitioned to FAILED (PaymentFailed): bookingId={}", bookingId);
    }

    @Override
    @Transactional
    public void onPaymentTimedOut(UUID eventId, UUID bookingId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate PaymentTimedOut: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();
        StateTransitionGuard.assertPaymentTransition(from, BookingStatus.EXPIRED);

        booking.setStatus(BookingStatus.EXPIRED);
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.EXPIRED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.PAYMENT_TIMEOUT));
        publishLifecycle(booking, EventType.BOOKING_EXPIRED);

        log.info("Booking transitioned to EXPIRED (PaymentTimedOut): bookingId={}", bookingId);
    }

    @Override
    @Transactional
    public void onInventoryReleased(UUID eventId, UUID bookingId) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate InventoryReleased: eventId={}, bookingId={}", eventId, bookingId);
            return;
        }

        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();

        // A Booking already CANCELLED (pre-confirmation path) is a no-op (EVT-005).
        if (from == BookingStatus.CANCELLED) {
            log.info("InventoryReleased on already-CANCELLED booking, no-op: bookingId={}", bookingId);
            consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.INVENTORY_RELEASED));
            return;
        }

        StateTransitionGuard.assertAllowed(from, BookingStatus.CANCELLED);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.CANCELLED));
        consumedEventRepository.save(new ConsumedEvent(eventId, ConsumerEventType.INVENTORY_RELEASED));

        log.info("Booking transitioned to CANCELLED (InventoryReleased): bookingId={}", bookingId);
    }

    // -------------------------------------------------------------------------
    // Expiration (safety-net scheduler)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void expireBooking(UUID bookingId) {
        Booking booking = findBooking(bookingId);
        BookingStatus from = booking.getStatus();

        if (from != BookingStatus.PENDING) {
            log.info("Skipping expiration, booking not PENDING: bookingId={}, status={}",
                    bookingId, from);
            return;
        }

        StateTransitionGuard.assertAllowed(from, BookingStatus.EXPIRED);

        booking.setStatus(BookingStatus.EXPIRED);
        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), from, BookingStatus.EXPIRED));
        publishLifecycle(booking, EventType.BOOKING_EXPIRED);

        log.info("Booking expired by scheduler: bookingId={}, from={}", bookingId, from);
    }

    // -------------------------------------------------------------------------
    // Catalog price validation
    // -------------------------------------------------------------------------

    private void validateCatalogPrice(BookingItemSelectionRequest item) {
        if (item.type() == BookingItemType.FLIGHT) {
            validateFlightPrice(item);
        } else if (item.type() == BookingItemType.HOTEL) {
            validateHotelPrice(item);
        }
    }

    private void validateFlightPrice(BookingItemSelectionRequest item) {
        FlightPriceResponse flightPrice;
        try {
            flightPrice = flightPriceClient.getFlightPrice(item.resourceId());
        } catch (FeignException e) {
            throw new CatalogUnavailableException(
                    "Flight service unavailable for flightId=" + item.resourceId(), e);
        }

        if (!ACTIVE_STATUS.equals(flightPrice.status())) {
            throw new CatalogValidationException(
                    "Flight " + item.resourceId() + " is not ACTIVE (status=" + flightPrice.status() + ")");
        }

        if (!pricesMatch(item.unitPrice(), flightPrice.basePrice())) {
            throw new CatalogValidationException(
                    "Price mismatch for flight " + item.resourceId()
                    + ": client=" + item.unitPrice().amount() + " " + item.unitPrice().currency()
                    + ", catalog=" + flightPrice.basePrice().amount() + " " + flightPrice.basePrice().currency());
        }
    }

    private void validateHotelPrice(BookingItemSelectionRequest item) {
        if (item.hotelId() == null) {
            throw new CatalogValidationException(
                    "hotelId is required for HOTEL items (roomTypeId=" + item.resourceId() + ")");
        }

        RoomTypePriceResponse roomPrice;
        try {
            roomPrice = hotelPriceClient.getRoomTypePrice(item.hotelId(), item.resourceId());
        } catch (FeignException e) {
            throw new CatalogUnavailableException(
                    "Hotel service unavailable for hotelId=" + item.hotelId()
                    + ", roomTypeId=" + item.resourceId(), e);
        }

        if (!ACTIVE_STATUS.equals(roomPrice.status())) {
            throw new CatalogValidationException(
                    "Room type " + item.resourceId() + " in hotel " + item.hotelId()
                    + " is not ACTIVE (status=" + roomPrice.status() + ")");
        }

        if (!pricesMatch(item.unitPrice(), roomPrice.pricePerNight())) {
            throw new CatalogValidationException(
                    "Price mismatch for room type " + item.resourceId()
                    + ": client=" + item.unitPrice().amount() + " " + item.unitPrice().currency()
                    + ", catalog=" + roomPrice.pricePerNight().amount() + " " + roomPrice.pricePerNight().currency());
        }
    }

    private boolean pricesMatch(MoneyDto clientPrice, MoneyDto catalogPrice) {
        return clientPrice.amount().compareTo(catalogPrice.amount()) == 0
                && clientPrice.currency().equals(catalogPrice.currency());
    }

    private BigDecimal getUSDPrice(BookingItemSelectionRequest item) {
        BigDecimal unitUSDPrice;
        if (item.unitPrice().currency().equals(CURRENCY_USD)) {
            unitUSDPrice = item.unitPrice().amount();

        } else {
            BigDecimal rateToUSD = getRateConversionToUSD(item.unitPrice().currency());
            unitUSDPrice = item.unitPrice().amount()
                    .divide(rateToUSD, CONVERSION_SCALE, RoundingMode.HALF_EVEN);
        }

        return unitUSDPrice;
    }

    private BigDecimal getRateConversionToUSD(String currency) {
        if (currency.equals(CURRENCY_USD)) return BigDecimal.ONE;
        List<ExchangeRateDto> exchangeRates;

        try {
          exchangeRates = exchangeRateClient.getUSDExchangeRates();
        } catch (FeignException e) {
            throw new PricingMismatchException(
                "Exchange Rate API unavailable for Currency=" + currency + ".Details: "+ e.getMessage());
        }
        return exchangeRates.stream()
            .filter(exchange ->
                exchange.quote().equals(currency)
            ).map(ExchangeRateDto::rate)
            .findFirst()
            .orElseThrow(() ->
                new PricingMismatchException(
                    "Currency "+ currency +" is not available in exchange rate catalog"));
    }

    private void validateTotalAmount(BigDecimal requestTotalAmount, BigDecimal calculatedTotalAmount) {
        if(requestTotalAmount.compareTo(calculatedTotalAmount) != 0) {
            log.error("Pricing mismatch: requestAmount={}, calculatedAmount={}"
                ,requestTotalAmount, calculatedTotalAmount);

            throw new PricingMismatchException(
                "Request Total Amount mismatch: "
                    + "ReqTotalAmount="+requestTotalAmount+
                    ", CalculatedTotalAmount="+calculatedTotalAmount);
        }
    }

    // -------------------------------------------------------------------------
    // Kafka payload builders
    // -------------------------------------------------------------------------

    private BookingCreatedPayload buildCreatedPayload(Booking booking) {
        List<BookingItemEvent> items = booking.getItems().stream()
                .map(item ->
                    new BookingItemEvent(
                        item.getType().name(),
                        item.getResourceId(),
                        item.getQuantity(),
                        item.getSubtotal()
                    )
                )
                .toList();
        return new BookingCreatedPayload(
                booking.getBookingId(),
                booking.getUserId(),
                items,
                booking.getTravelers().size(),
                new MoneyEvent(booking.getTotal().getAmount(), booking.getTotal().getCurrency()));
    }

    /** Writes a Booking lifecycle event (Confirmed/Failed/Expired) to the outbox (EVT-009). */
    private void publishLifecycle(Booking booking, EventType eventType) {
        var payload = new BookingLifecyclePayload(
            booking.getBookingId(),
            booking.getUserId(),
            booking.getStatus().name());
        outboxEventWriter.write(
            booking.getBookingId(),
            eventType,
            booking.getCorrelationId(),
            booking.getSagaId().toString(),
            payload);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private UUID extractUserId() {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("JWT principal expected but was: "
                + (principal == null ? "null" : principal.getClass().getName()));
    }

    private String hashRequest(CreateBookingRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash request payload", e);
        }
    }
}
