package com.atlas.booking.booking.messaging;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Adaptive scheduler for the outbox relay (ADR-0013). Polls fast while there is a backlog and
 * backs off exponentially toward {@code max-interval-ms} when the outbox is idle, so the relay
 * reacts quickly under traffic without hammering the database at rest.
 *
 * <p>Two levers combine:
 * <ul>
 *   <li><b>Drain-in-loop</b> — within one cycle, keep claiming batches (each in its own
 *       transaction, through the {@link OutboxRelay} proxy) until a claim returns less than a full
 *       {@link OutboxRelay#BATCH_LIMIT} batch, or {@code max-drain-loops} is reached. A traffic
 *       burst is thus cleared in a single cycle instead of over many fixed-interval polls.</li>
 *   <li><b>Dynamic interval</b> — a {@link org.springframework.scheduling.Trigger} whose next
 *       delay resets to {@code min-interval-ms} after any cycle that did work and doubles up to
 *       {@code max-interval-ms} after an idle cycle.</li>
 * </ul>
 *
 * <p>Per pod: each replica adapts to the disjoint batches it claims (FOR UPDATE SKIP LOCKED), so
 * replicas self-balance without coordination. Replaces the fixed {@code @Scheduled} poller.
 */
@Slf4j
@Configuration
public class OutboxRelayScheduler implements SchedulingConfigurer {

    private final OutboxRelay outboxRelay;
    private final long minIntervalMs;
    private final long maxIntervalMs;
    private final int maxDrainLoops;

    private volatile long nextDelayMs;

    public OutboxRelayScheduler(
            OutboxRelay outboxRelay,
            @Value("${atlas.outbox.min-interval-ms:250}") long minIntervalMs,
            @Value("${atlas.outbox.max-interval-ms:5000}") long maxIntervalMs,
            @Value("${atlas.outbox.max-drain-loops:10}") int maxDrainLoops) {
        this.outboxRelay = outboxRelay;
        this.minIntervalMs = minIntervalMs;
        this.maxIntervalMs = maxIntervalMs;
        this.maxDrainLoops = maxDrainLoops;
        this.nextDelayMs = minIntervalMs;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::pollAdaptively, triggerContext -> {
            Instant last = triggerContext.lastCompletion();
            Instant base = (last != null) ? last : Instant.now();
            return base.plusMillis(nextDelayMs);
        });
    }

    /** One adaptive cycle: drain the current backlog, then recompute the next delay. */
    void pollAdaptively() {
        int total = 0;
        int published;
        int loops = 0;
        do {
            published = outboxRelay.publishPending(); // proxied → one transaction per claimed batch
            total += published;
        } while (published >= OutboxRelay.BATCH_LIMIT && ++loops < maxDrainLoops);

        nextDelayMs = computeNextDelay(total);
        log.debug("Outbox relay cycle published {} event(s); next poll in {} ms", total, nextDelayMs);
    }

    private long computeNextDelay(int published) {
        if (published > 0) {
            return minIntervalMs;
        }

        return Math.clamp(nextDelayMs * 2, minIntervalMs, maxIntervalMs);
    }
}
