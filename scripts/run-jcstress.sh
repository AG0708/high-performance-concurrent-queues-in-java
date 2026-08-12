#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-quick}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_dir="${2:-$repo_root/artifacts/jcstress/$mode-$timestamp}"

case "$mode" in
  quick)
    cpu_limit=4
    arguments=(-m quick -f 1 -iters 3 -time 200)
    ;;
  release)
    cpu_limit=8
    arguments=(-m default -f 3 -iters 5 -time 1000)
    ;;
  *)
    printf 'usage: %s [quick|release] [output-directory]\n' "$0" >&2
    exit 2
    ;;
esac

available_cpus="$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.logicalcpu)"
if ((available_cpus < cpu_limit)); then
  cpu_count="$available_cpus"
else
  cpu_count="$cpu_limit"
fi
arguments+=(-c "$cpu_count")

cd "$repo_root"
./mvnw --batch-mode --no-transfer-progress -pl stridequeue-jcstress -am package -DskipTests
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
(
  cd "$output_dir"
  java -jar "$repo_root/stridequeue-jcstress/target/jcstress.jar" \
    "${arguments[@]}" \
    -r "$output_dir" \
    -t 'io.github.ag0708.stridequeue.jcstress.*' \
    2>&1 | tee "$output_dir/run.log"
)

result_blob_count="$(find "$output_dir" -maxdepth 1 -name 'jcstress-results-*.bin.gz' -print | wc -l | tr -d ' ')"
if [ "$result_blob_count" -ne 1 ]; then
  printf 'expected one JCStress result blob; found %s\n' "$result_blob_count" >&2
  exit 1
fi
result_blob="$(find "$output_dir" -maxdepth 1 -name 'jcstress-results-*.bin.gz' -print -quit)"
java -cp "$repo_root/stridequeue-jcstress/target/jcstress.jar" \
  io.github.ag0708.stridequeue.jcstress.JcstressResultSummary \
  "$result_blob" "$output_dir/summary.json"

printf 'JCStress report: %s/index.html\n' "$output_dir"
