# Correctness

## Fast gate

`scripts/check.sh` runs a clean Java 17 build with compiler warnings treated as errors, Javadoc validation, and 17 JUnit tests.

The tests cover:

- empty, full, wraparound, null, invalid-capacity, and batch boundaries;
- 800 randomized concurrent histories checked by exhaustive search against a bounded FIFO model;
- four-producer/four-consumer MPMC runs with 200,000 unique values per queue;
- four-producer/one-consumer single and batch MPSC runs;
- exact loss and duplication detection with one counter per value.

## JVM memory-model gate

`scripts/run-jcstress.sh quick` runs five JCStress tests:

- array-queue release/acquire publication;
- two concurrent MPMC producers;
- two concurrent MPSC producers;
- two contiguous batch reservations;
- Michael-Scott tail helping.

Every unlisted outcome is forbidden. The release mode increases forks, iterations, and per-iteration time.

The recorded release run produced 510 passing result records and 12,726,025,690 sampled outcomes with zero failed or interesting records. The raw stream, generated summary, checksums, and HTML report are under [`evidence/jcstress/2026-08-11-bcf2e14`](../evidence/jcstress/2026-08-11-bcf2e14/).

## What the tests do not prove

Testing cannot prove correctness for every schedule, JVM, or counter value. The exhaustive checker is bounded to small histories, stress runs are finite, and JCStress samples JVM executions. Each gate is independent enough to catch different classes of errors, but the progress and memory-order arguments in [DESIGN.md](DESIGN.md) remain necessary.
