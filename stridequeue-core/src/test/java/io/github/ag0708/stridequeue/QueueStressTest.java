package io.github.ag0708.stridequeue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QueueStressTest {
    private static final int PRODUCERS = 4;
    private static final int CONSUMERS = 4;
    private static final int VALUES_PER_PRODUCER = 50_000;

    @Test
    @Timeout(30)
    void arrayQueueDoesNotLoseOrDuplicateElements() throws Exception {
        exercise(new MpmcArrayQueue<>(1 << 12));
    }

    @Test
    @Timeout(30)
    void michaelScottQueueDoesNotLoseOrDuplicateElements() throws Exception {
        exercise(new MichaelScottQueue<>());
    }

    @Test
    @Timeout(30)
    void mpscQueueDoesNotLoseOrDuplicateElements() throws Exception {
        exercise(new MpscArrayQueue<>(1 << 12), 1);
    }

    @Test
    @Timeout(30)
    void mpscBatchQueueDoesNotLoseOrDuplicateElements() throws Exception {
        int batchSize = 16;
        int batchesPerProducer = VALUES_PER_PRODUCER / batchSize;
        int total = PRODUCERS * batchesPerProducer * batchSize;
        MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(1 << 12);
        AtomicIntegerArray seen = new AtomicIntegerArray(total);
        CountDownLatch ready = new CountDownLatch(PRODUCERS + 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(PRODUCERS + 1);
        List<Future<?>> futures = new ArrayList<>();

        for (int producer = 0; producer < PRODUCERS; producer++) {
            int first = producer * batchesPerProducer * batchSize;
            futures.add(
                    executor.submit(
                            () -> {
                                Integer[] batch = new Integer[batchSize];
                                ready.countDown();
                                await(start);
                                for (int batchNumber = 0;
                                        batchNumber < batchesPerProducer;
                                        batchNumber++) {
                                    for (int index = 0; index < batchSize; index++) {
                                        batch[index] = first + batchNumber * batchSize + index;
                                    }
                                    while (!queue.offerBatch(batch, 0, batchSize)) {
                                        Thread.onSpinWait();
                                    }
                                }
                            }));
        }

        futures.add(
                executor.submit(
                        () -> {
                            Integer[] batch = new Integer[batchSize];
                            int consumed = 0;
                            ready.countDown();
                            await(start);
                            while (consumed < total) {
                                int count = queue.pollBatch(batch, 0, batch.length);
                                if (count == 0) {
                                    Thread.onSpinWait();
                                    continue;
                                }
                                for (int index = 0; index < count; index++) {
                                    seen.incrementAndGet(batch[index]);
                                }
                                consumed += count;
                            }
                        }));

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        for (int value = 0; value < total; value++) {
            assertEquals(1, seen.get(value), "occurrences of value " + value);
        }
        assertNull(queue.poll());
    }

    private static void exercise(ConcurrentFifo<Integer> queue) throws Exception {
        exercise(queue, CONSUMERS);
    }

    private static void exercise(ConcurrentFifo<Integer> queue, int consumers) throws Exception {
        int total = PRODUCERS * VALUES_PER_PRODUCER;
        AtomicIntegerArray seen = new AtomicIntegerArray(total);
        AtomicInteger consumed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(PRODUCERS + consumers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(PRODUCERS + consumers);
        List<Future<?>> futures = new ArrayList<>();

        for (int producer = 0; producer < PRODUCERS; producer++) {
            int first = producer * VALUES_PER_PRODUCER;
            futures.add(
                    executor.submit(
                            () -> {
                                ready.countDown();
                                await(start);
                                for (int value = first;
                                        value < first + VALUES_PER_PRODUCER;
                                        value++) {
                                    while (!queue.offer(value)) {
                                        Thread.onSpinWait();
                                    }
                                }
                            }));
        }

        for (int consumer = 0; consumer < consumers; consumer++) {
            futures.add(
                    executor.submit(
                            () -> {
                                ready.countDown();
                                await(start);
                                while (consumed.get() < total) {
                                    Integer value = queue.poll();
                                    if (value == null) {
                                        Thread.onSpinWait();
                                        continue;
                                    }
                                    seen.incrementAndGet(value);
                                    consumed.incrementAndGet();
                                }
                            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        assertEquals(total, consumed.get());
        for (int value = 0; value < total; value++) {
            assertEquals(1, seen.get(value), "occurrences of value " + value);
        }
        assertNull(queue.poll());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test thread interrupted", exception);
        }
    }
}
