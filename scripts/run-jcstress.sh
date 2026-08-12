#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-quick}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_dir="${2:-$repo_root/artifacts/jcstress/$mode-$timestamp}"

case "$mode" in
  quick)
    arguments=(-m quick -c 4 -f 1 -iters 3 -time 200)
    ;;
  release)
    arguments=(-m default -c 8 -f 3 -iters 5 -time 1000)
    ;;
  *)
    printf 'usage: %s [quick|release] [output-directory]\n' "$0" >&2
    exit 2
    ;;
esac

cd "$repo_root"
./mvnw --batch-mode --no-transfer-progress -pl stridequeue-jcstress -am package -DskipTests
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
(
  cd "$output_dir"
  java -jar "$repo_root/stridequeue-jcstress/target/jcstress.jar" \
    "${arguments[@]}" \
    -r "$output_dir" \
    -t 'io.github.ag0708.stridequeue.jcstress.*'
)

printf 'JCStress report: %s/index.html\n' "$output_dir"
