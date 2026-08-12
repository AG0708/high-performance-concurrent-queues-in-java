#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-quick}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_dir="${2:-$repo_root/artifacts/benchmarks/$mode-$timestamp}"

case "$mode" in
  quick)
    timing=(-wi 2 -i 3 -w 500ms -r 500ms -f 1)
    ;;
  release)
    timing=(-wi 5 -i 7 -w 1s -r 1s -f 3)
    ;;
  *)
    printf 'usage: %s [quick|release] [output-directory]\n' "$0" >&2
    exit 2
    ;;
esac

cd "$repo_root"
./mvnw --batch-mode --no-transfer-progress -pl stridequeue-benchmarks -am package -DskipTests
mkdir -p "$output_dir"

{
  printf 'captured_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if git_commit="$(git rev-parse --verify HEAD 2>/dev/null)"; then
    printf 'git_commit=%s\n' "$git_commit"
  else
    printf 'git_commit=uncommitted\n'
  fi
  printf 'logical_cpus=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.logicalcpu)"
  uname -a
  java -version
} >"$output_dir/environment.txt" 2>&1

benchmark_jar="stridequeue-benchmarks/target/benchmarks.jar"

java -jar "$benchmark_jar" '^io\.github\.ag0708\.stridequeue\.benchmarks\.MpscBatchBenchmark\.transfers$' \
  -p implementation=stride-batch,array-blocking-loop \
  -p capacity=65536 \
  "${timing[@]}" -foe true -rf json \
  -rff "$output_dir/batch.json" -o "$output_dir/batch.log"

java -jar "$benchmark_jar" '^io\.github\.ag0708\.stridequeue\.benchmarks\.MpscQueueBenchmark\.transfers$' \
  -p implementation=stride-mpsc,array-blocking \
  -p capacity=65536 \
  "${timing[@]}" -foe true -rf json \
  -rff "$output_dir/mpsc-single.json" -o "$output_dir/mpsc-single.log"

java -jar "$benchmark_jar" '^io\.github\.ag0708\.stridequeue\.benchmarks\.QueueBenchmark\.transfers$' \
  -p implementation=stride-compact,stride-padded,array-blocking,michael-scott,jdk-clq \
  -p capacity=65536 -tg 2,2 \
  "${timing[@]}" -foe true -rf json \
  -rff "$output_dir/mpmc.json" -o "$output_dir/mpmc.log"

python3 scripts/summarize_benchmarks.py "$output_dir"
printf 'Benchmark report: %s/SUMMARY.md\n' "$output_dir"
