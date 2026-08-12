#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
benchmark_dir="$repo_root/evidence/benchmarks/2026-08-11-bcf2e14"
jcstress_dir="$repo_root/evidence/jcstress/2026-08-11-bcf2e14"

(cd "$benchmark_dir" && shasum -a 256 -c SHA256SUMS)
(cd "$jcstress_dir" && shasum -a 256 -c SHA256SUMS)

python3 - "$benchmark_dir/summary.json" "$jcstress_dir/summary.json" <<'PY'
import json
import math
import sys

benchmark = json.load(open(sys.argv[1], encoding="utf-8"))
jcstress = json.load(open(sys.argv[2], encoding="utf-8"))

assert benchmark["schema"] == "stridequeue.benchmark-summary.v1"
batch_ratio = benchmark["comparisons"]["batch_vs_array_blocking"]
batch_score = benchmark["batch"]["stride-batch"]["score"]
baseline_score = benchmark["batch"]["array-blocking-loop"]["score"]
assert math.isclose(batch_ratio, batch_score / baseline_score, rel_tol=1e-12)
assert batch_score > baseline_score

assert jcstress["schema"] == "stridequeue.jcstress-summary.v1"
assert jcstress["result_records"] == 510
assert jcstress["passing_records"] == jcstress["result_records"]
assert jcstress["failed_records"] == 0
assert jcstress["interesting_records"] == 0
assert jcstress["statuses"] == {"NORMAL": 510}
assert len(jcstress["tests"]) == 5
PY
