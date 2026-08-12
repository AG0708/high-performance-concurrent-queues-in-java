package io.github.ag0708.stridequeue.jcstress;

import io.github.ag0708.stridequeue.MpmcArrayQueue;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/** A consumer may miss a concurrent offer, but cannot observe partial publication. */
@JCStressTest
@Outcome(id = "-1", expect = Expect.ACCEPTABLE, desc = "Poll linearized before offer.")
@Outcome(id = "42", expect = Expect.ACCEPTABLE, desc = "Poll observed the published element.")
@Outcome(expect = Expect.FORBIDDEN, desc = "Unexpected or partially published value.")
@State
public class ArrayQueuePublicationTest {
    private final MpmcArrayQueue<Integer> queue = new MpmcArrayQueue<>(2);

    @Actor
    public void producer() {
        queue.offer(42);
    }

    @Actor
    public void consumer(I_Result result) {
        Integer value = queue.poll();
        result.r1 = value == null ? -1 : value;
    }
}
