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

## Release result

Apple M5, 10 cores, 16 GB RAM, OpenJDK 21.0.10, macOS 26.5.1. JMH reports 99.9% confidence errors.

| Workload | StrideQueue | JDK baseline | Ratio |
| --- | ---: | ---: | ---: |
| 3P/1C, batches of 32 | 380.9M +/- 62.0M ops/s | 141.8M +/- 6.9M ops/s | 2.69x |
| 3P/1C, single elements | 16.9M +/- 1.5M ops/s | 118.9M +/- 13.7M ops/s | 0.14x |

For 2P/2C single-element operations, `MpmcArrayQueue` reached 17.8M ops/s, `MichaelScottQueue` reached 18.5M ops/s, `ArrayBlockingQueue` reached 142.1M ops/s, and `ConcurrentLinkedQueue` reached 28.9M ops/s.

The result supports a batch-path claim only. `ArrayBlockingQueue` was faster for every recorded single-element workload. The prior 38M ops/s and 11x claim is omitted because its original raw data and method were unavailable.

Raw results: [`evidence/benchmarks/2026-08-11-bcf2e14`](../evidence/benchmarks/2026-08-11-bcf2e14/)

## Reproduce

```bash
scripts/run-benchmarks.sh release
```

Do not compare results across machines or JVMs without preserving the raw metadata and equivalent queue semantics.
