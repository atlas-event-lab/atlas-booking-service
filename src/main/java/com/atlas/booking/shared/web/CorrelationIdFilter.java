package com.atlas.booking.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Sets {@code correlationId} and {@code traceId} in MDC for every request (OBS-001, OBS-002).
 * Both values are propagated in response headers so downstream services can continue the trace.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER       = "X-Trace-Id";

    public static final String MDC_KEY          = "correlationId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String correlationId = resolve(request.getHeader(CORRELATION_ID_HEADER));
        String traceId       = resolve(request.getHeader(TRACE_ID_HEADER));

        MDC.put(MDC_KEY,          correlationId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(TRACE_ID_HEADER,       traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolve(String header) {
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
