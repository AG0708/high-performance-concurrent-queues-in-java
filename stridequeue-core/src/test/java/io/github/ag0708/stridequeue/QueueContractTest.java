package io.github.ag0708.stridequeue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueueContractTest {
    @Test
    void arrayQueuePreservesFifoAcrossWraparound() {
        MpmcArrayQueue<Integer> queue = new MpmcArrayQueue<>(4);

        assertEquals(MpmcArrayQueue.SlotSpacing.COMPACT, queue.slotSpacing());
        for (int value = 0; value < 4; value++) {
            assertTrue(queue.offer(value));
        }
        assertFalse(queue.offer(4));
        assertEquals(0, queue.poll());
        assertEquals(1, queue.poll());
        assertTrue(queue.offer(4));
        assertTrue(queue.offer(5));

        for (int value = 2; value < 6; value++) {
            assertEquals(value, queue.poll());
        }
        assertNull(queue.poll());
    }

    @Test
    void compactArrayQueueHasTheSameBoundaryBehavior() {
        MpmcArrayQueue<String> queue =
                new MpmcArrayQueue<>(2, MpmcArrayQueue.SlotSpacing.COMPACT);

        assertEquals(2, queue.capacity());
        assertEquals(MpmcArrayQueue.SlotSpacing.COMPACT, queue.slotSpacing());
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertFalse(queue.offer("third"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void linkedQueuePreservesFifo() {
        MichaelScottQueue<String> queue = new MichaelScottQueue<>();

        assertNull(queue.poll());
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void mpscQueuePreservesFifoAcrossWraparound() {
        MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(2);

        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertFalse(queue.offer(3));
        assertEquals(1, queue.poll());
        assertTrue(queue.offer(3));
        assertEquals(2, queue.poll());
        assertEquals(3, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void mpscBatchOperationsAreAllOrNothingAndPreserveOrder() {
        MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(4);
        Integer[] first = {1, 2, 3};
        Integer[] second = {4, 5};
        Integer[] output = new Integer[4];

        assertTrue(queue.offerBatch(first, 0, first.length));
        assertFalse(queue.offerBatch(second, 0, second.length));
        assertEquals(2, queue.pollBatch(output, 1, 2));
        assertEquals(1, output[1]);
        assertEquals(2, output[2]);
        assertTrue(queue.offerBatch(second, 0, second.length));
        assertEquals(3, queue.pollBatch(output, 0, output.length));
        assertEquals(List.of(3, 4, 5), List.of(output[0], output[1], output[2]));
    }

    @Test
    void mpscBatchValidationDoesNotPartiallyPublish() {
        MpscArrayQueue<Number> queue = new MpscArrayQueue<>(4);
        Number[] invalid = {1, null, 3};

        assertThrows(NullPointerException.class, () -> queue.offerBatch(invalid, 0, 3));
        assertNull(queue.poll());

        assertTrue(queue.offer(1.5));
        Integer[] incompatible = new Integer[1];
        assertThrows(ArrayStoreException.class, () -> queue.pollBatch(incompatible, 0, 1));
        assertEquals(1.5, queue.poll());
    }

    @Test
    void mpscBatchCannotConsumeAWrappedSlotTwice() {
        MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(2);
        Integer[] output = new Integer[4];

        assertTrue(queue.offerBatch(new Integer[] {1, 2}, 0, 2));
        assertEquals(2, queue.pollBatch(output, 0, output.length));
        assertEquals(List.of(1, 2), List.of(output[0], output[1]));
        assertNull(output[2]);
        assertNull(output[3]);
        assertNull(queue.poll());
    }

    @Test
    void queuesRejectNullBeforeMutatingState() {
        MpmcArrayQueue<Integer> arrayQueue = new MpmcArrayQueue<>(2);
        MpscArrayQueue<Integer> mpscQueue = new MpscArrayQueue<>(2);
        MichaelScottQueue<Integer> linkedQueue = new MichaelScottQueue<>();

        assertThrows(NullPointerException.class, () -> arrayQueue.offer(null));
        assertThrows(NullPointerException.class, () -> mpscQueue.offer(null));
        assertThrows(NullPointerException.class, () -> linkedQueue.offer(null));
        assertNull(arrayQueue.poll());
        assertNull(mpscQueue.poll());
        assertNull(linkedQueue.poll());
    }

    @Test
    void arrayQueueRejectsInvalidCapacities() {
        assertThrows(IllegalArgumentException.class, () -> new MpmcArrayQueue<>(0));
        assertThrows(IllegalArgumentException.class, () -> new MpmcArrayQueue<>(1));
        assertThrows(IllegalArgumentException.class, () -> new MpmcArrayQueue<>(3));
        assertThrows(IllegalArgumentException.class, () -> new MpscArrayQueue<>(3));
    }
}
