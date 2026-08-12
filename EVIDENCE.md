# Evidence map

| Claim | Implementation | Verification |
| --- | --- | --- |
| Lock-free MPMC FIFO | `MichaelScottQueue` | Linearizability histories, 200,000-value MPMC stress, JCStress tail-helping test |
| Fixed-capacity MPMC FIFO | `MpmcArrayQueue` | Boundary tests, linearizability histories, 200,000-value MPMC stress, JCStress publication and claim tests |
| Contiguous MPSC batches | `MpscArrayQueue.offerBatch/pollBatch` | Boundary and rollback tests, 200,000-value batch stress, JCStress reservation test |
| 510 passing JCStress records | Five JCStress tests | Raw result stream, generated summary, HTML report, and checksums under `evidence/jcstress/2026-08-11-bcf2e14` |
| 380.9M ops/s; 2.69x baseline | `MpscBatchBenchmark` | Three-fork JMH JSON, logs, environment, and checksums under `evidence/benchmarks/2026-08-11-bcf2e14` |

The benchmark claim is limited to three producers, one consumer, 32-element batches, capacity 65,536, OpenJDK 21.0.10, and the recorded Apple M5 host. Single-element results are slower than the JDK bounded baseline and are published beside the positive result.
