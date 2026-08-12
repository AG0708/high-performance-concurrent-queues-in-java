package io.github.ag0708.stridequeue;

/**
 * Minimal FIFO contract shared by the queue implementations in this project.
 *
 * <p>{@code null} is reserved to report an empty queue and cannot be inserted.
 * Implementations document their own capacity and progress guarantees.
 *
 * @param <E> element type
 */
public interface ConcurrentFifo<E> {
    /**
     * Attempts to append an element.
     *
     * @param element non-null element
     * @return {@code true} when accepted, or {@code false} when a bounded queue is full
     */
    boolean offer(E element);

    /**
     * Removes the oldest available element.
     *
     * @return the element, or {@code null} when the queue is empty
     */
    E poll();
}
