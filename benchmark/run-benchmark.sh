#!/usr/bin/env sh
set -eu

if [ "$#" -lt 1 ]; then
  echo "usage: $0 TARGET_URL [OUTPUT_JSON]" >&2
  exit 2
fi

target_url="$1"
output_path="${2:-benchmark-result.json}"
shift
python3 benchmark/run_benchmark.py --target-url "$target_url" --output "$output_path" "$@"
