# Benchmarks

## Method

The release benchmark uses JMH 1.37 with three forks, five one-second warmups, seven one-second measurements, a fixed 1 GiB heap, and heap pre-touch.

- Capacity: 65,536 elements
- MPSC: three producers and one consumer
- MPMC: two producers and two consumers
- Batch size: 32
- Score: successful enqueue and dequeue operations per second
- Baselines: `ArrayBlockingQueue` for bounded queues and `ConcurrentLinkedQueue` for the unbounded lock-free queue

Full/empty retries are exposed as secondary counters. Failed attempts are not presented as useful throughput. Raw JSON, all iterations, logs, JVM details, host details, and the tested commit are retained with the release evidence.

## Current status

A short prerelease run showed that batch claims materially reduce shared atomic updates. It also showed that `ArrayBlockingQueue` is faster than this implementation for single-element contention on the current Apple Silicon host.

The repository will report the longer multi-fork values here after the release run. The prior 38M ops/s and 11x claim is intentionally omitted because its original raw data and method were unavailable.

## Reproduce

```bash
scripts/run-benchmarks.sh release
```

Do not compare results across machines or JVMs without preserving the raw metadata and equivalent queue semantics.
