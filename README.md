# StrideQueue

Concurrent FIFO queue implementations for Java 17.

## Implementations

| Queue | Producers | Consumers | Capacity | Progress |
| --- | ---: | ---: | --- | --- |
| `MichaelScottQueue` | many | many | unbounded | lock-free |
| `MpmcArrayQueue` | many | many | fixed | non-blocking API |
| `MpscArrayQueue` | many | one | fixed | non-blocking API |

The array queues use `VarHandle` acquire/release operations and allocate no
nodes after construction. `MpscArrayQueue` also supports batch operations. The
bounded queues are not lock-free: a delayed producer can stall a reserved slot.

## Use

```java
MpscArrayQueue<Event> queue = new MpscArrayQueue<>(65_536);

queue.offer(event);        // false when full
Event next = queue.poll(); // null when empty; one consumer only
```

Use `MichaelScottQueue` for lock-free progress. Use an array queue for fixed
memory and no per-operation allocation.

## Test

```sh
scripts/check.sh
scripts/run-jcstress.sh quick
scripts/run-benchmarks.sh quick
```

## Benchmark

On an Apple M5 with three producers, one consumer, and batches of 32, the MPSC
batch path averaged 380.9M operations/sec, 2.69x the `ArrayBlockingQueue` loop.
Single-element results were slower than the JDK baseline.

See [BENCHMARKS.md](docs/BENCHMARKS.md) and the
[raw results](evidence/benchmarks/2026-08-11-bcf2e14/).

## License

MIT. See [`LICENSE`](LICENSE).
