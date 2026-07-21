package com.atlas.booking.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.booking.booking.entity.OutboxEvent;
import com.atlas.booking.shared.messaging.EventType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the core guarantee of ADR-0013: the {@code FOR UPDATE SKIP LOCKED} claim query hands
 * two concurrent relay instances <em>disjoint</em> batches, so no outbox row is ever published
 * twice. This is the property a plain {@code SELECT} (the pre-ADR-0013 relay) violated, causing
 * the duplicate-event storm observed in experiment 01.
 *
 * <p>Requires Docker (Testcontainers spins up a real Postgres — {@code SKIP LOCKED} is Postgres
 * row-lock behaviour that H2 cannot faithfully reproduce). The same claim query is shared verbatim
 * by inventory / payment / flight / hotel, so exercising it once here covers the mechanism.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@Import(OutboxSkipLockedConcurrencyTest.AuditingConfig.class)
@Testcontainers
class OutboxSkipLockedConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    PlatformTransactionManager txManager;

    /** {@code @CreatedDate} needs auditing active for the slice to populate {@code created_at}. */
    @EnableJpaAuditing
    static class AuditingConfig {}

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // each thread manages its own tx
    void concurrentClaims_areDisjoint_soNoRowIsPublishedTwice() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Seed 200 PENDING rows and commit them so the two claim transactions can see them.
        tx.executeWithoutResult(status -> {
            List<OutboxEvent> seed = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                UUID bookingId = UUID.randomUUID();
                seed.add(new OutboxEvent(
                        UUID.randomUUID(),
                        "Booking",
                        bookingId,
                        EventType.BOOKING_CREATED,
                        1,
                        "{\"eventType\":\"BookingCreated\",\"payload\":{\"bookingId\":\"" + bookingId + "\"}}"));
            }
            outboxRepository.saveAll(seed);
        });

        // Two "relay pods": each opens a transaction, claims a batch, then waits at the barrier so
        // both hold their row locks simultaneously before either commits. SKIP LOCKED must make the
        // second claimer skip the rows the first already locked.
        CyclicBarrier bothClaimed = new CyclicBarrier(2);
        Callable<List<UUID>> claimTask = () -> tx.execute(status -> {
            List<UUID> ids = outboxRepository.claimBatchForPublishing().stream()
                    .map(OutboxEvent::getId)
                    .toList();
            try {
                bothClaimed.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return ids;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<UUID>> a = pool.submit(claimTask);
            Future<List<UUID>> b = pool.submit(claimTask);
            List<UUID> claimedByA = a.get(30, TimeUnit.SECONDS);
            List<UUID> claimedByB = b.get(30, TimeUnit.SECONDS);

            // Each claimed a full LIMIT-100 batch, with zero overlap and no lost rows.
            assertThat(claimedByA).hasSize(100);
            assertThat(claimedByB).hasSize(100);
            assertThat(claimedByA).doesNotContainAnyElementsOf(claimedByB);
            List<UUID> union = new ArrayList<>(claimedByA);
            union.addAll(claimedByB);
            assertThat(union).doesNotHaveDuplicates().hasSize(200);
        } finally {
            pool.shutdownNow();
        }
    }
}
