package com.echcherqaoui.jobboard.jobservice.idempotency.impl;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdempotencyGuardImplRaceConditionIT extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyGuardImpl guard;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void concurrentCallsWithSameEventId_demonstratesNonAtomicCheckThenAct() throws Exception {
        String eventId = "race-evt-1";
        int threadCount = 16;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        AtomicInteger unprocessedCount = new AtomicInteger(0); // returned false
        AtomicInteger processedCount = new AtomicInteger(0);   // returned true
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(); // Maximizes concurrent collision at the exact same instant
                    boolean isAlreadyProcessed = guard.isProcessed(eventId);
                    if (isAlreadyProcessed) {
                        processedCount.incrementAndGet();
                    } else {
                        unprocessedCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                }
            });
        }

        executor.shutdown();
        boolean completed = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Exactly ONE row must exist in PostgreSQL regardless of thread safety issues.
        assertThat(processedEventRepository.count()).isEqualTo(1);

        // Either an unhandled DB exception was thrown OR multiple threads observed false (unprocessed).
        boolean constraintViolated = exceptions.stream().anyMatch(t ->
              t instanceof DataIntegrityViolationException ||
                    (t.getCause() != null && t.getCause() instanceof DataIntegrityViolationException)
        );

        boolean multipleThreadsAttemptedInsert = unprocessedCount.get() > 1;

        assertThat(constraintViolated || multipleThreadsAttemptedInsert)
              .as("Expected race condition: either multiple threads received 'false' or DB thrown DataIntegrityViolationException")
              .isTrue();
    }

    @Test
    void sequentialCalls_secondCallReturnsTrue_noException() {
        String eventId = "sequential-evt-1";

        boolean first = guard.isProcessed(eventId);
        boolean second = guard.isProcessed(eventId);

        assertThat(first).isFalse();
        assertThat(second).isTrue();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}