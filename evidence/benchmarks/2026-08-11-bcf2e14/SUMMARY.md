# Benchmark summary

JMH throughput; capacity 65,536; successful enqueue and dequeue operations are both counted.

| Workload | StrideQueue | JDK baseline | Ratio |
| --- | ---: | ---: | ---: |
| 3P/1C, batches of 32 | 380.9 M ops/s | 141.8 M ops/s | 2.69x |
| 3P/1C, single elements | 16.9 M ops/s | 118.9 M ops/s | 0.14x |

The batch comparison uses `MpscArrayQueue.offerBatch/pollBatch`; the baseline loops over
`ArrayBlockingQueue.offer/poll`. See the raw JSON and logs in this directory.

## 2P/2C single-element results

| Implementation | Throughput |
| --- | ---: |
| stride-compact | 17.8 M ops/s |
| stride-padded | 17.3 M ops/s |
| array-blocking | 142.1 M ops/s |
| michael-scott | 18.5 M ops/s |
| jdk-clq | 28.9 M ops/s |
