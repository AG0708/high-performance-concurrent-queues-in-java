package io.github.ag0708.stridequeue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ag0708.stridequeue.QueueLinearizabilityChecker.Operation;
import io.github.ag0708.stridequeue.QueueLinearizabilityChecker.Recorder;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class QueueLinearizabilityTest {
    private static final int CAPACITY = 2;
    private static final int THREADS = 3;
    private static final int OPERATIONS_PER_THREAD = 2;
    private static final int HISTORIES_PER_IMPLEMENTATION = 400;

    @Test
    void checkerAcceptsAnOverlappingFifoHistory() {
        List<Operation> history =
                List.of(
                        Operation.offer(1, true, 0, 3),
                        Operation.offer(2, true, 1, 4),
                        Operation.poll(1, 2, 5),
                        Operation.poll(2, 6, 7));

        assertTrue(QueueLinearizabilityChecker.isLinearizable(history, 2));
    }

    @Test
    void checkerRejectsARealTimeFifoViolation() {
        List<Operation> history =
                List.of(
                        Operation.offer(1, true, 0, 1),
                        Operation.offer(2, true, 2, 3),
                        Operation.poll(2, 4, 5));

        assertFalse(QueueLinearizabilityChecker.isLinearizable(history, 2));
    }

    @Test
    void arrayQueueHistoriesAreLinearizable() throws Exception {
        checkRandomHistories(() -> new MpmcArrayQueue<>(CAPACITY), CAPACITY, 0x51D3A77AL);
    }

    @Test
    void michaelScottHistoriesAreLinearizable() throws Exception {
        checkRandomHistories(
                MichaelScottQueue::new,
                THREADS * OPERATIONS_PER_THREAD,
                0x4D53434CL);
    }

    private static void checkRandomHistories(QueueFactory factory, int capacity, long seed)
            throws Exception {
        SplittableRandom seeds = new SplittableRandom(seed);
        for (int historyNumber = 0;
                historyNumber < HISTORIES_PER_IMPLEMENTATION;
                historyNumber++) {
            ConcurrentFifo<Integer> queue = factory.create();
            Recorder recorder = new Recorder();
            AtomicLong clock = new AtomicLong();
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            List<Future<?>> futures = new ArrayList<>();

            for (int thread = 0; thread < THREADS; thread++) {
                long threadSeed = seeds.nextLong();
                int threadNumber = thread;
                futures.add(
                        executor.submit(
                                () -> runHistoryThread(
                                        queue,
                                        recorder,
                                        clock,
                                        ready,
                                        start,
                                        threadSeed,
                                        threadNumber)));
            }

            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            List<Operation> history = recorder.snapshot();
            assertTrue(
                    QueueLinearizabilityChecker.isLinearizable(history, capacity),
                    () -> "non-linearizable history at seed " + seed + ": " + history);
        }
    }

    private static void runHistoryThread(
            ConcurrentFifo<Integer> queue,
            Recorder recorder,
            AtomicLong clock,
            CountDownLatch ready,
            CountDownLatch start,
            long seed,
            int threadNumber) {
        SplittableRandom random = new SplittableRandom(seed);
        ready.countDown();
        await(start);

        for (int operation = 0; operation < OPERATIONS_PER_THREAD; operation++) {
            if (random.nextBoolean()) {
                int value = threadNumber * OPERATIONS_PER_THREAD + operation + 1;
                long invokedAt = clock.incrementAndGet();
                boolean result = queue.offer(value);
                long completedAt = clock.incrementAndGet();
                recorder.recordOffer(value, result, invokedAt, completedAt);
            } else {
                long invokedAt = clock.incrementAndGet();
                Integer result = queue.poll();
                long completedAt = clock.incrementAndGet();
                recorder.recordPoll(result, invokedAt, completedAt);
            }
            if (random.nextBoolean()) {
                Thread.yield();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test thread interrupted", exception);
        }
    }

    @FunctionalInterface
    private interface QueueFactory {
        ConcurrentFifo<Integer> create();
    }
}
