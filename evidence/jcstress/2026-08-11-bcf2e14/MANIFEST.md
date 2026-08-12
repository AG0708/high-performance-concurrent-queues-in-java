# Release JCStress evidence

- Tested commit: `bcf2e14aea5d095876be1048e4a68b6a63b3a4bd`
- Command: `scripts/run-jcstress.sh release artifacts/jcstress/release-bcf2e14`
- Harness: JCStress 0.16
- JVM: OpenJDK 21.0.10
- Host: Apple M5, 8 of 10 logical cores used
- Configuration: default mode, 3 normal forks, 15 stress forks, 5 iterations, 1 second per iteration

The raw serialized result stream contains 510 result records and 12,726,025,690 sampled outcomes. All 510 records have normal status and pass their outcome grading; no record is failed or interesting. `summary.json` is generated directly from the raw stream by `JcstressResultSummary`. The HTML files are the harness report.
