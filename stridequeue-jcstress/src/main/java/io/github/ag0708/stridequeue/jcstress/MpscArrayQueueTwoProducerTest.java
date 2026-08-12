package io.github.ag0708.stridequeue.jcstress;

import io.github.ag0708.stridequeue.MpscArrayQueue;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/** Concurrent slot claims must retain both elements in reservation order. */
@JCStressTest
@Outcome(id = "1, 2", expect = Expect.ACCEPTABLE, desc = "Producer one claimed first.")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "Producer two claimed first.")
@Outcome(expect = Expect.FORBIDDEN, desc = "Lost, duplicated, or unpublished element.")
@State
public class MpscArrayQueueTwoProducerTest {
    private final MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(2);

    @Actor
    public void producerOne() {
        queue.offer(1);
    }

    @Actor
    public void producerTwo() {
        queue.offer(2);
    }

    @Arbiter
    public void observe(II_Result result) {
        result.r1 = valueOrSentinel(queue.poll());
        result.r2 = valueOrSentinel(queue.poll());
    }

    private static int valueOrSentinel(Integer value) {
        return value == null ? -1 : value;
    }
}
