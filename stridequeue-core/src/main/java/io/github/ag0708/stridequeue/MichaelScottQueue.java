package io.github.ag0708.stridequeue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * Unbounded multi-producer, multi-consumer FIFO using the Michael-Scott algorithm.
 *
 * <p>Both {@link #offer(Object)} and {@link #poll()} are linearizable and lock-free.
 * A stalled thread cannot prevent other threads from completing operations. Nodes
 * are reclaimed by the JVM, which also prevents address-reuse ABA on node links.
 *
 * @param <E> element type
 * @see <a href="https://www.cs.rochester.edu/research/synchronization/pseudocode/queues.html">Michael-Scott queue algorithm</a>
 */
public final class MichaelScottQueue<E> implements ConcurrentFifo<E> {
    private final PaddedAtomicReference<Node<E>> head;
    private final PaddedAtomicReference<Node<E>> tail;

    /** Creates an empty queue. */
    public MichaelScottQueue() {
        Node<E> sentinel = new Node<>(null);
        head = new PaddedAtomicReference<>(sentinel);
        tail = new PaddedAtomicReference<>(sentinel);
    }

    @Override
    public boolean offer(E element) {
        Node<E> node = new Node<>(Objects.requireNonNull(element, "element"));

        while (true) {
            Node<E> observedTail = tail.getVolatile();
            Node<E> next = observedTail.nextVolatile();

            if (observedTail != tail.getVolatile()) {
                Thread.onSpinWait();
                continue;
            }

            if (next == null) {
                if (observedTail.link(null, node)) {
                    tail.compareAndSet(observedTail, node);
                    return true;
                }
            } else {
                tail.compareAndSet(observedTail, next);
            }
            Thread.onSpinWait();
        }
    }

    @Override
    public E poll() {
        while (true) {
            Node<E> observedHead = head.getVolatile();
            Node<E> observedTail = tail.getVolatile();
            Node<E> next = observedHead.nextVolatile();

            if (observedHead != head.getVolatile()) {
                Thread.onSpinWait();
                continue;
            }

            if (next == null) {
                return null;
            }

            if (observedHead == observedTail) {
                tail.compareAndSet(observedTail, next);
                Thread.onSpinWait();
                continue;
            }

            E value = next.value;
            if (head.compareAndSet(observedHead, next)) {
                next.value = null;
                return value;
            }
            Thread.onSpinWait();
        }
    }

    private static final class Node<E> {
        private static final VarHandle NEXT;

        static {
            try {
                NEXT = MethodHandles.lookup().findVarHandle(Node.class, "next", Node.class);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private E value;
        private volatile Node<E> next;

        private Node(E value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        private Node<E> nextVolatile() {
            return (Node<E>) NEXT.getVolatile(this);
        }

        private boolean link(Node<E> expected, Node<E> update) {
            return NEXT.compareAndSet(this, expected, update);
        }
    }
}
