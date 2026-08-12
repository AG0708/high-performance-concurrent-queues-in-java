package io.github.ag0708.stridequeue.benchmarks;

import io.github.ag0708.stridequeue.ConcurrentFifo;
import io.github.ag0708.stridequeue.MichaelScottQueue;
import io.github.ag0708.stridequeue.MpmcArrayQueue;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
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

/** Balanced producer/consumer contention benchmark. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 7, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch"})
@State(Scope.Group)
public class QueueBenchmark {
    private static final Integer ELEMENT = 1;

    @Param({"stride-padded", "stride-compact", "array-blocking", "michael-scott", "jdk-clq"})
    private String implementation;

    @Param("65536")
    private int capacity;

    private ConcurrentFifo<Integer> queue;

    /** Recreates and half-fills the queue before each iteration. */
    @Setup(Level.Iteration)
    public void setUp() {
        queue = createQueue(implementation, capacity);
        for (int index = 0; index < capacity / 2; index++) {
            if (!queue.offer(ELEMENT)) {
                throw new IllegalStateException("failed to prefill queue");
            }
        }
    }

    /** Measures one non-blocking offer attempt. */
    @Benchmark
    @Group("attempts")
    @GroupThreads(1)
    public boolean offer(Counters counters) {
        boolean accepted = queue.offer(ELEMENT);
        if (accepted) {
            counters.offersAccepted++;
        } else {
            counters.offersRejected++;
        }
        return accepted;
    }

    /** Measures one non-blocking poll attempt. */
    @Benchmark
    @Group("attempts")
    @GroupThreads(1)
    public Integer poll(Counters counters) {
        Integer element = queue.poll();
        if (element == null) {
            counters.pollsEmpty++;
        } else {
            counters.pollsWithElement++;
        }
        return element;
    }

    /** Measures one successful enqueue, including retries while the queue is full. */
    @Benchmark
    @Group("transfers")
    @GroupThreads(1)
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

    /** Measures one successful dequeue, including retries while the queue is empty. */
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

    private static ConcurrentFifo<Integer> createQueue(String name, int capacity) {
        return switch (name) {
            case "stride-padded" ->
                    new MpmcArrayQueue<>(capacity, MpmcArrayQueue.SlotSpacing.PADDED);
            case "stride-compact" ->
                    new MpmcArrayQueue<>(capacity, MpmcArrayQueue.SlotSpacing.COMPACT);
            case "array-blocking" -> new JdkQueueAdapter<>(new ArrayBlockingQueue<>(capacity));
            case "michael-scott" -> new MichaelScottQueue<>();
            case "jdk-clq" -> new JdkQueueAdapter<>(new ConcurrentLinkedQueue<>());
            default -> throw new IllegalArgumentException("unknown queue: " + name);
        };
    }

    /** Per-thread counters expose unsuccessful attempts alongside throughput. */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counters {
        public long offersAccepted;
        public long offersRejected;
        public long pollsWithElement;
        public long pollsEmpty;
    }

    /** Per-thread retry counts make queue saturation visible. */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class RetryCounters {
        public long offerRetries;
        public long pollRetries;
    }

    private static final class JdkQueueAdapter<E> implements ConcurrentFifo<E> {
        private final Queue<E> delegate;

        private JdkQueueAdapter(Queue<E> delegate) {
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
