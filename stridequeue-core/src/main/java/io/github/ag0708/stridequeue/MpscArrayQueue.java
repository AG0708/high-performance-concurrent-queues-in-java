package io.github.ag0708.stridequeue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * Fixed-capacity, allocation-free queue for multiple producers and one consumer.
 *
 * <p>Producers reserve slots with one compare-and-set on a shared cursor. The
 * single consumer owns its cursor, so dequeue does not require a compare-and-set.
 * Element publication and slot reuse use release/acquire ordering.
 *
 * <p>The caller must not invoke {@link #poll()} concurrently from more than one
 * thread. The queue is linearizable under that contract. It is non-blocking in
 * the API sense but not lock-free: a producer paused after reserving a slot can
 * delay the consumer at that FIFO position.
 *
 * @param <E> element type
 */
public final class MpscArrayQueue<E> implements ConcurrentFifo<E> {
    private static final VarHandle ELEMENT = MethodHandles.arrayElementVarHandle(Object[].class);

    private final int capacity;
    private final int mask;
    private final Object[] elements;
    private final StripedLongs cursors;

    private static final int PRODUCER_POSITION = 0;
    private static final int PRODUCER_LIMIT = 1;
    private static final int CONSUMER_POSITION = 2;

    /**
     * Creates an empty queue with a power-of-two capacity.
     *
     * @param capacity power-of-two capacity of at least two
     */
    public MpscArrayQueue(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two and at least 2");
        }
        this.capacity = capacity;
        mask = capacity - 1;
        elements = new Object[capacity];
        cursors = new StripedLongs(0L, capacity, 0L);
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
     * Atomically reserves and publishes a contiguous batch.
     *
     * <p>The method performs one cursor compare-and-set for the entire batch. It
     * returns {@code false} without modifying the queue when there is not enough
     * room for every element.
     *
     * @param source source array
     * @param offset first source index
     * @param length number of elements to append
     * @return whether the complete batch was accepted
     */
    public boolean offerBatch(E[] source, int offset, int length) {
        Objects.requireNonNull(source, "source");
        Objects.checkFromIndexSize(offset, length, source.length);
        if (length > capacity) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            Objects.requireNonNull(source[offset + index], "source element");
        }
        if (length == 0) {
            return true;
        }

        long position = cursors.getVolatile(PRODUCER_POSITION);
        while (true) {
            long limit = cursors.getVolatile(PRODUCER_LIMIT);
            if (position > limit - length) {
                long refreshedLimit = cursors.getVolatile(CONSUMER_POSITION) + capacity;
                if (position > refreshedLimit - length) {
                    return false;
                }
                cursors.compareAndSet(PRODUCER_LIMIT, limit, refreshedLimit);
                position = cursors.getVolatile(PRODUCER_POSITION);
                continue;
            }

            if (cursors.compareAndSet(PRODUCER_POSITION, position, position + length)) {
                for (int batchIndex = 0; batchIndex < length; batchIndex++) {
                    int queueIndex = (int) (position + batchIndex) & mask;
                    ELEMENT.setRelease(elements, queueIndex, source[offset + batchIndex]);
                }
                return true;
            }
            position = cursors.getVolatile(PRODUCER_POSITION);
            Thread.onSpinWait();
        }
    }

    /**
     * Removes up to {@code maxElements} with one consumer-cursor publication.
     *
     * <p>This method follows the same single-consumer rule as {@link #poll()}.
     * Values are staged in the destination before queue state is changed, so an
     * incompatible destination array cannot leave the queue half-consumed.
     *
     * @param destination destination array
     * @param offset first destination index
     * @param maxElements maximum number of elements to remove
     * @return number of elements removed
     */
    @SuppressWarnings("unchecked")
    public int pollBatch(E[] destination, int offset, int maxElements) {
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(offset, maxElements, destination.length);
        if (maxElements == 0) {
            return 0;
        }

        int batchLimit = Math.min(maxElements, capacity);
        long firstPosition = cursors.getVolatile(CONSUMER_POSITION);
        int count = 0;
        while (count < batchLimit) {
            long position = firstPosition + count;
            int queueIndex = (int) position & mask;
            E element = (E) ELEMENT.getAcquire(elements, queueIndex);
            if (element == null) {
                if (cursors.getVolatile(PRODUCER_POSITION) == position) {
                    break;
                }
                do {
                    Thread.onSpinWait();
                    element = (E) ELEMENT.getAcquire(elements, queueIndex);
                } while (element == null);
            }
            destination[offset + count] = element;
            count++;
        }

        for (int batchIndex = 0; batchIndex < count; batchIndex++) {
            int queueIndex = (int) (firstPosition + batchIndex) & mask;
            ELEMENT.setRelease(elements, queueIndex, null);
        }
        if (count > 0) {
            cursors.setRelease(CONSUMER_POSITION, firstPosition + count);
        }
        return count;
    }

    @Override
    public boolean offer(E element) {
        Objects.requireNonNull(element, "element");
        long position = cursors.getVolatile(PRODUCER_POSITION);

        while (true) {
            long limit = cursors.getVolatile(PRODUCER_LIMIT);
            if (position >= limit) {
                long refreshedLimit = cursors.getVolatile(CONSUMER_POSITION) + capacity;
                if (position >= refreshedLimit) {
                    return false;
                }
                cursors.compareAndSet(PRODUCER_LIMIT, limit, refreshedLimit);
                position = cursors.getVolatile(PRODUCER_POSITION);
                continue;
            }

            if (cursors.compareAndSet(PRODUCER_POSITION, position, position + 1L)) {
                int index = (int) position & mask;
                ELEMENT.setRelease(elements, index, element);
                return true;
            }
            position = cursors.getVolatile(PRODUCER_POSITION);
            Thread.onSpinWait();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        long position = cursors.getVolatile(CONSUMER_POSITION);
        int index = (int) position & mask;
        E element = (E) ELEMENT.getAcquire(elements, index);

        if (element == null) {
            if (cursors.getVolatile(PRODUCER_POSITION) == position) {
                return null;
            }
            do {
                Thread.onSpinWait();
                element = (E) ELEMENT.getAcquire(elements, index);
            } while (element == null);
        }

        ELEMENT.setRelease(elements, index, null);
        cursors.setRelease(CONSUMER_POSITION, position + 1L);
        return element;
    }
}
