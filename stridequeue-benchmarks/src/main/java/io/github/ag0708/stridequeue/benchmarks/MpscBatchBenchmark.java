package io.github.ag0708.stridequeue.benchmarks;

import io.github.ag0708.stridequeue.MpscArrayQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;

/** Measures 32-element producer claims and consumer drains. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 7, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch"})
@State(Scope.Group)
public class MpscBatchBenchmark {
    private static final int BATCH_SIZE = 32;
    private static final Integer ELEMENT = 1;

    @Param({"stride-batch", "array-blocking-loop"})
    private String implementation;

    @Param("65536")
    private int capacity;

    private MpscArrayQueue<Integer> strideQueue;
    private ArrayBlockingQueue<Integer> blockingQueue;

    /** Recreates and half-fills the selected queue before each iteration. */
    @Setup(Level.Iteration)
    public void setUp() {
        strideQueue = null;
        blockingQueue = null;
        switch (implementation) {
            case "stride-batch" -> strideQueue = new MpscArrayQueue<>(capacity);
            case "array-blocking-loop" -> blockingQueue = new ArrayBlockingQueue<>(capacity);
            default -> throw new IllegalArgumentException("unknown queue: " + implementation);
        }

        for (int index = 0; index < capacity / 2; index++) {
            boolean accepted =
                    strideQueue != null
                            ? strideQueue.offer(ELEMENT)
                            : blockingQueue.offer(ELEMENT);
            if (!accepted) {
                throw new IllegalStateException("failed to prefill queue");
            }
        }
    }

    /** Claims or loops over 32 successful offers. Score units are elements/second. */
    @Benchmark
    @Group("transfers")
    @GroupThreads(3)
    @OperationsPerInvocation(BATCH_SIZE)
    public boolean putBatch(Control control, Buffers buffers, RetryCounters counters) {
        if (strideQueue != null) {
            while (!strideQueue.offerBatch(buffers.source, 0, BATCH_SIZE)) {
                counters.producerWaits++;
                if (control.stopMeasurement) {
                    return false;
                }
                Thread.onSpinWait();
            }
            return true;
        }

        for (int index = 0; index < BATCH_SIZE; index++) {
            while (!blockingQueue.offer(ELEMENT)) {
                counters.producerWaits++;
                if (control.stopMeasurement) {
                    return false;
                }
                Thread.onSpinWait();
            }
        }
        return true;
    }

    /** Drains or loops over 32 successful polls. Score units are elements/second. */
    @Benchmark
    @Group("transfers")
    @GroupThreads(1)
    @OperationsPerInvocation(BATCH_SIZE)
    public Integer takeBatch(Control control, Buffers buffers, RetryCounters counters) {
        if (strideQueue != null) {
            int consumed = 0;
            while (consumed < BATCH_SIZE) {
                int drained =
                        strideQueue.pollBatch(
                                buffers.destination, consumed, BATCH_SIZE - consumed);
                if (drained == 0) {
                    counters.consumerWaits++;
                    if (control.stopMeasurement) {
                        return null;
                    }
                    Thread.onSpinWait();
                } else {
                    consumed += drained;
                }
            }
            return buffers.destination[BATCH_SIZE - 1];
        }

        Integer element = null;
        for (int index = 0; index < BATCH_SIZE; index++) {
            while ((element = blockingQueue.poll()) == null) {
                counters.consumerWaits++;
                if (control.stopMeasurement) {
                    return null;
                }
                Thread.onSpinWait();
            }
        }
        return element;
    }

    /** Reusable arrays keep allocation outside the measured operations. */
    @State(Scope.Thread)
    public static class Buffers {
        private final Integer[] source = new Integer[BATCH_SIZE];
        private final Integer[] destination = new Integer[BATCH_SIZE];

        /** Initializes the immutable producer batch. */
        @Setup(Level.Trial)
        public void setUp() {
            java.util.Arrays.fill(source, ELEMENT);
        }
    }

    /** Spin counts reveal queue-full and queue-empty pressure. */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class RetryCounters {
        public long producerWaits;
        public long consumerWaits;
    }
}
