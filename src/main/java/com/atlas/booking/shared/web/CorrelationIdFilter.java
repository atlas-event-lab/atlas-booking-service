package com.atlas.booking.shared.web;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Seeds a business {@code correlationId} as OpenTelemetry baggage for every request (OBS-001, OBS-002).
 *
 * <p>The distributed {@code traceId}/{@code spanId} are owned by Micrometer Tracing (OTLP -> Tempo) and
 * injected into the MDC automatically.
 *
 * <p>{@code correlationId} is a baggage field ({@code management.tracing.baggage.remote-fields}) so it
 * propagates across REST (Feign) and Kafka, and a correlation field
 * ({@code management.tracing.baggage.correlation.fields}) so Micrometer copies it into the MDC/logs.
 * This filter only resolves it (existing baggage -> inbound header -> new UUID) and opens its scope.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * MDC keys populated by Micrometer (correlationId via baggage correlation-fields, traceId by
     * tracing). Kept as shared constants for read-only consumers (OutboxEventWriter,
     * GlobalExceptionHandler); this filter does not write them directly.
     */
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private final Tracer tracer;

    public CorrelationIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try (BaggageInScope ignored = tracer.createBaggageInScope(MDC_CORRELATION_ID, correlationId)) {
            chain.doFilter(request, response);
        }
    }

    /** Prefer a correlationId already propagated as baggage; else an inbound header; else generate one. */
    private String resolveCorrelationId(HttpServletRequest request) {
        Baggage existing = tracer.getBaggage(MDC_CORRELATION_ID);
        if (existing != null) {
            String value = existing.get();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String header = request.getHeader(CORRELATION_ID_HEADER);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
