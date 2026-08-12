# StrideQueue

StrideQueue is a Java 17 library for studying concurrent FIFO design. It includes a lock-free linked queue, bounded array queues, stress tests, a linearizability checker, JCStress tests, and JMH benchmarks.

## Implementations

| Queue | Producers | Consumers | Capacity | Progress |
| --- | ---: | ---: | --- | --- |
| `MichaelScottQueue` | many | many | unbounded | lock-free |
| `MpmcArrayQueue` | many | many | fixed | non-blocking API; a reserved slot can stall progress |
| `MpscArrayQueue` | many | one | fixed | non-blocking API; a reserved slot can stall progress |

The array queues use `VarHandle` acquire/release operations, separated producer and consumer cursors, and no allocation after construction. `MpscArrayQueue` also supports 32-element-style batch claims and drains with one shared cursor update per batch.

## Run it

Java 17 or newer is required. Maven is downloaded by the checked and pinned wrapper.

```bash
scripts/check.sh
scripts/run-jcstress.sh quick
scripts/run-benchmarks.sh quick
```

Library use is deliberately small:

```java
MpscArrayQueue<Event> queue = new MpscArrayQueue<>(65_536);

queue.offer(event);        // false when full
Event next = queue.poll(); // null when empty; one consumer only
```

Use `MichaelScottQueue` when strict lock-freedom matters. Use an array queue when fixed memory and no per-operation allocation matter.

## Evidence

- JUnit checks boundaries, wraparound, batch behavior, and 800 randomized histories against an exhaustive FIFO model.
- MPMC stress tests verify that every unique value is consumed exactly once.
- JCStress exercises publication, concurrent claims, tail helping, and contiguous batch reservation across JVM compilation modes.
- JMH reports successful operations, retries, raw samples, confidence intervals, JVM flags, and host details.

The original resume claim of 38M ops/s and an 11x speedup was not carried forward without evidence. Current results and exact limitations are recorded in [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

## Repository map

- `stridequeue-core`: queue implementations and JUnit tests
- `stridequeue-jcstress`: JVM memory-model tests
- `stridequeue-benchmarks`: JMH workloads
- `docs`: design, correctness, and benchmark notes
- `scripts`: one-command verification and evidence capture

See [DESIGN.md](docs/DESIGN.md) for invariants and [CORRECTNESS.md](docs/CORRECTNESS.md) for the test strategy.
