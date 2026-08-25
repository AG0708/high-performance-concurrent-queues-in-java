#!/usr/bin/env python3
"""Validate JMH result files and produce a small comparison report."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


def load_results(path: Path) -> dict[str, dict[str, Any]]:
    rows = json.loads(path.read_text(encoding="utf-8"))
    results: dict[str, dict[str, Any]] = {}
    for row in rows:
        metric = row["primaryMetric"]
        if metric["scoreUnit"] != "ops/s":
            raise ValueError(f"unexpected unit in {path}: {metric['scoreUnit']}")
        implementation = row["params"]["implementation"]
        if implementation in results:
            raise ValueError(f"duplicate implementation in {path}: {implementation}")
        results[implementation] = {
            "score": metric["score"],
            "score_error": metric["scoreError"],
            "confidence": metric["scoreConfidence"],
            "unit": metric["scoreUnit"],
        }
    return results


def require(results: dict[str, dict[str, Any]], name: str) -> dict[str, Any]:
    if name not in results:
        raise ValueError(f"missing benchmark implementation: {name}")
    return results[name]


def format_rate(value: float) -> str:
    return f"{value / 1_000_000:.1f} M ops/s"


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} RESULT_DIRECTORY", file=sys.stderr)
        return 2

    result_dir = Path(sys.argv[1]).resolve()
    batch = load_results(result_dir / "batch.json")
    mpsc = load_results(result_dir / "mpsc-single.json")
    mpmc = load_results(result_dir / "mpmc.json")

    batch_stride = require(batch, "stride-batch")["score"]
    batch_jdk = require(batch, "array-blocking-loop")["score"]
    single_stride = require(mpsc, "stride-mpsc")["score"]
    single_jdk = require(mpsc, "array-blocking")["score"]

    summary = {
        "schema": "stridequeue.benchmark-summary.v1",
        "methodology": {
            "capacity": 65536,
            "batch_size": 32,
            "mpsc_threads": {"producers": 3, "consumers": 1},
            "mpmc_threads": {"producers": 2, "consumers": 2},
            "score": "successful queue operations per second",
        },
        "batch": batch,
        "mpsc_single": mpsc,
        "mpmc": mpmc,
        "comparisons": {
            "batch_vs_array_blocking": batch_stride / batch_jdk,
            "single_mpsc_vs_array_blocking": single_stride / single_jdk,
        },
    }
    (result_dir / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    lines = [
        "# Benchmark summary",
        "",
        "JMH throughput; capacity 65,536; successful enqueue and dequeue operations are both counted.",
        "",
        "| Workload | Custom queue | JDK baseline | Ratio |",
        "| --- | ---: | ---: | ---: |",
        (
            "| 3P/1C, batches of 32 | "
            f"{format_rate(batch_stride)} | {format_rate(batch_jdk)} | "
            f"{batch_stride / batch_jdk:.2f}x |"
        ),
        (
            "| 3P/1C, single elements | "
            f"{format_rate(single_stride)} | {format_rate(single_jdk)} | "
            f"{single_stride / single_jdk:.2f}x |"
        ),
        "",
        "The batch comparison uses `MpscArrayQueue.offerBatch/pollBatch`; the baseline loops over",
        "`ArrayBlockingQueue.offer/poll`. See the raw JSON and logs in this directory.",
        "",
        "## 2P/2C single-element results",
        "",
        "| Implementation | Throughput |",
        "| --- | ---: |",
    ]
    for implementation in (
        "stride-compact",
        "stride-padded",
        "array-blocking",
        "michael-scott",
        "jdk-clq",
    ):
        score = require(mpmc, implementation)["score"]
        lines.append(f"| {implementation} | {format_rate(score)} |")
    lines.append("")
    (result_dir / "SUMMARY.md").write_text("\n".join(lines), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
