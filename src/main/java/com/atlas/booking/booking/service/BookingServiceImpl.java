package com.atlas.booking.booking.service;

import com.atlas.booking.booking.client.SearchClient;
import com.atlas.booking.booking.client.dto.TripDetailResponse;
import com.atlas.booking.booking.client.dto.TripItemResponse;
import com.atlas.booking.booking.dto.BookingItemSelectionRequest;
import com.atlas.booking.booking.dto.BookingResponse;
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
import com.atlas.booking.booking.exception.BookingNotFoundException;
import com.atlas.booking.booking.exception.IdempotencyConflictException;
import com.atlas.booking.booking.exception.PricingMismatchException;
import com.atlas.booking.booking.exception.TripNotFoundException;
import com.atlas.booking.booking.mapper.BookingMapper;
import com.atlas.booking.booking.messaging.BookingCreatedApplicationEvent;
import com.atlas.booking.booking.messaging.BookingLifecycleApplicationEvent;
import com.atlas.booking.booking.repository.BookingRepository;
import com.atlas.booking.booking.repository.ConsumedEventRepository;
import com.atlas.booking.shared.messaging.EventTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
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

        BookingCreatedPayload kafkaPayload = buildCreatedPayload(saved);
        eventPublisher.publishEvent(
                new BookingCreatedApplicationEvent(this, saved.getBookingId(),
                        saved.getCorrelationId(), saved.getSagaId(), kafkaPayload));

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
    // Saga choreography handlers (Phase 6)
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
        consumedEventRepository.save(new ConsumedEvent(eventId, "InventoryReserved"));

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
        consumedEventRepository.save(new ConsumedEvent(eventId, "InventoryRejected"));
        eventPublisher.publishEvent(buildLifecycleEvent(booking, "BookingFailed", EventTopics.BOOKING_FAILED));

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
        consumedEventRepository.save(new ConsumedEvent(eventId, "PaymentSucceeded"));
        eventPublisher.publishEvent(buildLifecycleEvent(booking, "BookingConfirmed", EventTopics.BOOKING_CONFIRMED));

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
        consumedEventRepository.save(new ConsumedEvent(eventId, "PaymentFailed"));
        eventPublisher.publishEvent(buildLifecycleEvent(booking, "BookingFailed", EventTopics.BOOKING_FAILED));

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
        consumedEventRepository.save(new ConsumedEvent(eventId, "PaymentTimedOut"));
        eventPublisher.publishEvent(buildLifecycleEvent(booking, "BookingExpired", EventTopics.BOOKING_EXPIRED));

        log.info("Booking transitioned to EXPIRED (PaymentTimedOut): bookingId={}", bookingId);
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
                        item.getQuantity()
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

    private BookingLifecycleApplicationEvent buildLifecycleEvent(Booking booking,
                                                                  String eventType,
                                                                  String topic) {
        var payload = new BookingLifecyclePayload(
                booking.getBookingId(),
                booking.getUserId(),
                booking.getStatus().name());
        return new BookingLifecycleApplicationEvent(
                this, eventType, topic,
                booking.getBookingId(),
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
