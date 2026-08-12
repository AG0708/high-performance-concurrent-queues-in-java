package io.github.ag0708.stridequeue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * Fixed-capacity multi-producer, multi-consumer FIFO backed by a ring buffer.
 *
 * <p>Each slot carries a monotonically increasing sequence number. Producers and
 * consumers reserve positions with one compare-and-set, then publish or recycle a
 * slot with release ordering. The queue does not allocate after construction.
 *
 * <p>Operations are linearizable, but this implementation is not lock-free: a
 * thread paused after reserving the next slot can make a consumer wait for that
 * slot to be published. Use {@link MichaelScottQueue} when strict lock-freedom is
 * required.
 *
 * @param <E> element type
 * @see <a href="https://www.1024cores.net/home/lock-free-algorithms/queues/bounded-mpmc-queue">Vyukov bounded MPMC queue</a>
 */
public final class MpmcArrayQueue<E> implements ConcurrentFifo<E> {
    private static final VarHandle ELEMENT = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle SEQUENCE = MethodHandles.arrayElementVarHandle(long[].class);

    private final int capacity;
    private final int mask;
    private final int sequenceStride;
    private final Object[] elements;
    private final long[] sequences;
    private final StripedLongs cursors = new StripedLongs(0L, 0L);

    private static final int PRODUCER_POSITION = 0;
    private static final int CONSUMER_POSITION = 1;

    /**
     * Creates a queue with compact per-slot sequence counters.
     *
     * @param capacity power-of-two capacity of at least two
     */
    public MpmcArrayQueue(int capacity) {
        this(capacity, SlotSpacing.COMPACT);
    }

    /**
     * Creates a queue with the requested sequence-counter layout.
     *
     * @param capacity power-of-two capacity of at least two
     * @param spacing compact or cache-line-separated sequence counters
     */
    public MpmcArrayQueue(int capacity, SlotSpacing spacing) {
        Objects.requireNonNull(spacing, "spacing");
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two and at least 2");
        }

        sequenceStride = spacing.stride();
        int sequenceLength;
        try {
            sequenceLength = Math.multiplyExact(capacity, sequenceStride);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("capacity is too large for the selected spacing", exception);
        }

        this.capacity = capacity;
        mask = capacity - 1;
        elements = new Object[capacity];
        sequences = new long[sequenceLength];

        for (int index = 0; index < capacity; index++) {
            sequences[sequenceOffset(index)] = index;
        }
    }

    /**
     * Returns the fixed number of elements this queue can hold.
     *
     * @return queue capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the configured sequence-counter layout.
     *
     * @return slot spacing
     */
    public SlotSpacing slotSpacing() {
        return sequenceStride == SlotSpacing.COMPACT.stride()
                ? SlotSpacing.COMPACT
                : SlotSpacing.PADDED;
    }

    @Override
    public boolean offer(E element) {
        Objects.requireNonNull(element, "element");
        long position = cursors.getVolatile(PRODUCER_POSITION);

        while (true) {
            int index = (int) position & mask;
            int sequenceOffset = sequenceOffset(index);
            long sequence = (long) SEQUENCE.getAcquire(sequences, sequenceOffset);
            long difference = sequence - position;

            if (difference == 0L) {
                if (cursors.compareAndSet(PRODUCER_POSITION, position, position + 1L)) {
                    ELEMENT.set(elements, index, element);
                    SEQUENCE.setRelease(sequences, sequenceOffset, position + 1L);
                    return true;
                }
            } else if (difference < 0L) {
                return false;
            }

            position = cursors.getVolatile(PRODUCER_POSITION);
            Thread.onSpinWait();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        long position = cursors.getVolatile(CONSUMER_POSITION);

        while (true) {
            int index = (int) position & mask;
            int sequenceOffset = sequenceOffset(index);
            long expectedSequence = position + 1L;
            long sequence = (long) SEQUENCE.getAcquire(sequences, sequenceOffset);
            long difference = sequence - expectedSequence;

            if (difference == 0L) {
                if (cursors.compareAndSet(CONSUMER_POSITION, position, position + 1L)) {
                    E element = (E) ELEMENT.get(elements, index);
                    ELEMENT.set(elements, index, null);
                    SEQUENCE.setRelease(sequences, sequenceOffset, position + capacity);
                    return element;
                }
            } else if (difference < 0L) {
                if (cursors.getVolatile(PRODUCER_POSITION) == position) {
                    return null;
                }
            } else {
                position = cursors.getVolatile(CONSUMER_POSITION);
            }

            Thread.onSpinWait();
        }
    }

    private int sequenceOffset(int logicalIndex) {
        return logicalIndex * sequenceStride;
    }

    /** Controls the memory used between sequence counters for adjacent slots. */
    public enum SlotSpacing {
        /** One sequence counter per slot. */
        COMPACT(1),

        /** Eight longs per slot, separating counters by 64 bytes. */
        PADDED(8);

        private final int stride;

        SlotSpacing(int stride) {
            this.stride = stride;
        }

        private int stride() {
            return stride;
        }
    }
}
