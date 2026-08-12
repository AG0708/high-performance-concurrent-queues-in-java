# Release benchmark evidence

- Tested commit: `bcf2e14aea5d095876be1048e4a68b6a63b3a4bd`
- Command: `scripts/run-benchmarks.sh release artifacts/benchmarks/release-bcf2e14`
- Harness: JMH 1.37
- JVM: OpenJDK 21.0.10
- Host: Apple M5, 10 cores, 16 GB RAM, macOS 26.5.1
- Configuration: 3 forks, 5 x 1-second warmups, 7 x 1-second measurements, 1 GiB pre-touched heap

`summary.json` is generated from the three raw JMH JSON files. `SUMMARY.md` is the human-readable view. Logs contain every warmup, measurement, secondary retry counter, confidence interval, and command setting. `SHA256SUMS` covers all generated files.

The batch score counts successful enqueue and dequeue operations. It does not count a transferred item once; each item contributes one enqueue and one dequeue operation.
