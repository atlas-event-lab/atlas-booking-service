package com.atlas.booking.booking.service;

import com.atlas.booking.booking.client.SearchClient;
import com.atlas.booking.booking.client.dto.TripDetailResponse;
import com.atlas.booking.booking.client.dto.TripItemResponse;
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
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.exception.TripNotFoundException;
import com.atlas.booking.booking.mapper.BookingMapper;
import com.atlas.booking.booking.messaging.OutboxEventWriter;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.repository.ConsumedEventRepository;
import com.atlas.booking.shared.messaging.EventType;
import com.atlas.booking.shared.messaging.ConsumerEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Booking Service implementation.
 * Handles pricing validation via Search Service, idempotency, state-transition
 * guarding, Booking persistence, and Saga choreography participation.
 * All entity-to-DTO mapping is performed here before returning to callers
 * (coding-standards §Layer Responsibilities).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final SearchClient searchClient;
    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final BookingMapper bookingMapper;

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

        // Resolve Trip from Search — read-only, pricing validation only (ARCH-003, ARCH-006)
        TripDetailResponse tripDetail = searchClient.getTrip(request.tripId())
                .orElseThrow(() -> new TripNotFoundException(request.tripId()));

        // Recompute Grand Total and validate against Trip total (SPEC-DOMAIN-PRICING)
        Money total = computeAndValidateTotal(tripDetail, request.items());

        UUID bookingId    = UUID.randomUUID();
        UUID sagaId       = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Booking booking = new Booking(
                bookingId, userId, request.tripId(),
                BookingStatus.PENDING, total, correlationId, sagaId, idempotencyKey, incomingHash);

        Map<UUID, TripItemResponse> tripItemsByResourceId = tripDetail.items().stream()
                .collect(Collectors.toMap(TripItemResponse::resourceId, Function.identity()));

        for (BookingItemSelectionRequest itemRequest : request.items()) {
            TripItemResponse tripItem = tripItemsByResourceId.get(itemRequest.resourceId());
            BigDecimal unitPrice = tripItem.price().amount();
            BigDecimal subtotal  = unitPrice
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            booking.addItem(new BookingItem(
                    UUID.randomUUID(),
                    BookingItemType.valueOf(tripItem.type()),
                    itemRequest.resourceId(),
                    itemRequest.quantity(),
                    unitPrice,
                    subtotal));
        }

        for (var travelerRequest : request.travelers()) {
            booking.addTraveler(new Traveler(
                    UUID.randomUUID(),
                    travelerRequest.firstName(),
                    travelerRequest.lastName(),
                    travelerRequest.dateOfBirth(),
                    travelerRequest.nationality(),
                    travelerRequest.documentType(),
                    travelerRequest.documentNumber(),
                    travelerRequest.email(),
                    travelerRequest.phoneNumber()));
        }

        booking.addStatusHistory(new BookingStatusHistory(UUID.randomUUID(), null, BookingStatus.PENDING));

        Booking saved = bookingRepository.save(booking);

        // Write BookingCreated to the outbox in this same transaction (EVT-009).
        outboxEventWriter.write(
            saved.getBookingId(),
            EventType.BOOKING_CREATED,
            saved.getCorrelationId(),
            saved.getSagaId().toString(),
            buildCreatedPayload(saved));

        log.info("Booking created: bookingId={}, userId={}, tripId={}, sagaId={}, correlationId={}",
                bookingId, userId, request.tripId(), sagaId, correlationId);

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
        StateTransitionGuard.assertAllowed(from, BookingStatus.CONFIRMED);

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
        StateTransitionGuard.assertAllowed(from, BookingStatus.FAILED);

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
        StateTransitionGuard.assertAllowed(from, BookingStatus.EXPIRED);

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
    // Pricing (SPEC-DOMAIN-PRICING)
    // -------------------------------------------------------------------------

    /**
     * Recomputes the Grand Total from selected items and validates it against the
     * Trip total returned by Search. Formula: sum(unitPrice × quantity) per item.
     * All items must be present in the Trip; currencies must be identical.
     */
    private Money computeAndValidateTotal(TripDetailResponse tripDetail,
                                          List<BookingItemSelectionRequest> selectedItems) {
        Map<UUID, TripItemResponse> tripItemsByResourceId = tripDetail.items().stream()
                .collect(Collectors.toMap(TripItemResponse::resourceId, Function.identity()));

        String currency = tripDetail.total().currency();
        BigDecimal recomputed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (BookingItemSelectionRequest item : selectedItems) {
            TripItemResponse tripItem = tripItemsByResourceId.get(item.resourceId());
            if (tripItem == null) {
                throw new PricingMismatchException("Item not found in Trip: resourceId=" + item.resourceId());
            }
            if (!currency.equals(tripItem.price().currency())) {
                throw new PricingMismatchException(
                        "Currency mismatch for resourceId=" + item.resourceId()
                        + ": expected=" + currency + ", got=" + tripItem.price().currency());
            }
            recomputed = recomputed.add(tripItem.price().amount()
                    .multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_UP));
        }

        recomputed = recomputed.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tripTotal = tripDetail.total().amount().setScale(2, RoundingMode.HALF_UP);

        if (recomputed.compareTo(tripTotal) != 0) {
            throw new PricingMismatchException(
                    "Recomputed total " + recomputed + " " + currency
                    + " does not match Trip total " + tripTotal + " " + currency);
        }

        return new Money(recomputed, currency);
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
                booking.getTripId(),
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
