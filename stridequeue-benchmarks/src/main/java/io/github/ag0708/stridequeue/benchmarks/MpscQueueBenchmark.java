package io.github.ag0708.stridequeue.benchmarks;

import io.github.ag0708.stridequeue.ConcurrentFifo;
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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;

/** Multiple-producer, single-consumer successful-transfer benchmark. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 7, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch"})
@State(Scope.Group)
public class MpscQueueBenchmark {
    private static final Integer ELEMENT = 1;

    @Param({"stride-mpsc", "array-blocking"})
    private String implementation;

    @Param("65536")
    private int capacity;

    private ConcurrentFifo<Integer> queue;

    /** Recreates and half-fills the queue before each iteration. */
    @Setup(Level.Iteration)
    public void setUp() {
        queue =
                switch (implementation) {
                    case "stride-mpsc" -> new MpscArrayQueue<>(capacity);
                    case "array-blocking" ->
                            new BlockingQueueAdapter<>(new ArrayBlockingQueue<>(capacity));
                    default -> throw new IllegalArgumentException(
                            "unknown queue: " + implementation);
                };
        for (int index = 0; index < capacity / 2; index++) {
            if (!queue.offer(ELEMENT)) {
                throw new IllegalStateException("failed to prefill queue");
            }
        }
    }

    /** Measures a successful producer operation, including full-queue retries. */
    @Benchmark
    @Group("transfers")
    @GroupThreads(3)
    public boolean put(Control control, RetryCounters counters) {
        while (!queue.offer(ELEMENT)) {
            counters.offerRetries++;
            if (control.stopMeasurement) {
                return false;
            }
            Thread.onSpinWait();
        }
        return true;
    }

    /** Measures the single consumer, including empty-queue retries. */
    @Benchmark
    @Group("transfers")
    @GroupThreads(1)
    public Integer take(Control control, RetryCounters counters) {
        Integer element;
        while ((element = queue.poll()) == null) {
            counters.pollRetries++;
            if (control.stopMeasurement) {
                return null;
            }
            Thread.onSpinWait();
        }
        return element;
    }

    /** Per-thread retry counts make queue saturation visible. */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class RetryCounters {
        public long offerRetries;
        public long pollRetries;
    }

    private static final class BlockingQueueAdapter<E> implements ConcurrentFifo<E> {
        private final ArrayBlockingQueue<E> delegate;

        private BlockingQueueAdapter(ArrayBlockingQueue<E> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean offer(E element) {
            return delegate.offer(element);
        }

        @Override
        public E poll() {
            return delegate.poll();
        }
    }
}
