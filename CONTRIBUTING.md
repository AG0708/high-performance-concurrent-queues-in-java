# Contributing

Run the fast gate before opening a change:

```bash
scripts/check.sh
scripts/run-jcstress.sh quick
```

Changes to queue state, memory ordering, or linearization points must update `docs/DESIGN.md` and add a focused regression. Benchmark changes must preserve raw JMH output and cannot count failed offers or empty polls as completed transfers.
