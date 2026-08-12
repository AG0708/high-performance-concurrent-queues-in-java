package io.github.ag0708.stridequeue.jcstress;

import io.github.ag0708.stridequeue.MpscArrayQueue;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.IIII_Result;

/** Concurrent batch claims must stay contiguous and retain every element. */
@JCStressTest
@Outcome(id = "1, 2, 3, 4", expect = Expect.ACCEPTABLE, desc = "First batch claimed first.")
@Outcome(id = "3, 4, 1, 2", expect = Expect.ACCEPTABLE, desc = "Second batch claimed first.")
@Outcome(expect = Expect.FORBIDDEN, desc = "Interleaved, lost, or unpublished batch element.")
@State
public class MpscBatchReservationTest {
    private final MpscArrayQueue<Integer> queue = new MpscArrayQueue<>(4);

    @Actor
    public void producerOne() {
        queue.offerBatch(new Integer[] {1, 2}, 0, 2);
    }

    @Actor
    public void producerTwo() {
        queue.offerBatch(new Integer[] {3, 4}, 0, 2);
    }

    @Arbiter
    public void observe(IIII_Result result) {
        Integer[] values = new Integer[4];
        int count = queue.pollBatch(values, 0, values.length);
        result.r1 = count > 0 ? values[0] : -1;
        result.r2 = count > 1 ? values[1] : -1;
        result.r3 = count > 2 ? values[2] : -1;
        result.r4 = count > 3 ? values[3] : -1;
    }
}
