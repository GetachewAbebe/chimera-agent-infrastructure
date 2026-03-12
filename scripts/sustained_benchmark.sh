#!/usr/bin/env bash
set -euo pipefail

if [[ ! -x "scripts/load_validation.sh" ]]; then
  echo "scripts/load_validation.sh is required and must be executable"
  exit 1
fi

ROUNDS="${ROUNDS:-5}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-200}"
CONCURRENCY="${CONCURRENCY:-40}"
OUTPUT_DIR="${OUTPUT_DIR:-artifacts/benchmarks/$(date -u +%Y%m%dT%H%M%SZ)}"
REPORT_PATH="${REPORT_PATH:-$OUTPUT_DIR/summary.md}"
CSV_PATH="$OUTPUT_DIR/rounds.csv"

if [[ "$ROUNDS" -lt 1 ]]; then
  echo "ROUNDS must be >= 1"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
echo "round,throughput_rps,created,rate_limited,unexpected,latency_avg_ms,latency_p95_ms,latency_max_ms,elapsed_seconds" >"$CSV_PATH"

for round in $(seq 1 "$ROUNDS"); do
  round_json="$OUTPUT_DIR/round_${round}.json"
  round_log="$OUTPUT_DIR/round_${round}.log"

  round_output="$(
    TOTAL_REQUESTS="$TOTAL_REQUESTS" \
      CONCURRENCY="$CONCURRENCY" \
      GOAL_PREFIX="Sustained benchmark round ${round}" \
      OUTPUT_JSON_PATH="$round_json" \
      bash scripts/load_validation.sh
  )"

  echo "$round_output" | tee "$round_log"

  throughput="$(echo "$round_output" | awk -F': ' '/Approx throughput req\/s/{print $2}' | tail -1)"
  created="$(echo "$round_output" | awk -F': ' '/HTTP 201 created/{print $2}' | tail -1)"
  rate_limited="$(echo "$round_output" | awk -F': ' '/HTTP 429 rate_limited/{print $2}' | tail -1)"
  unexpected="$(echo "$round_output" | awk -F': ' '/Unexpected statuses/{print $2}' | tail -1)"
  latency_avg="$(echo "$round_output" | awk -F': ' '/Latency avg ms \(201\)/{print $2}' | tail -1)"
  latency_p95="$(echo "$round_output" | awk -F': ' '/Latency p95 ms \(201\)/{print $2}' | tail -1)"
  latency_max="$(echo "$round_output" | awk -F': ' '/Latency max ms \(201\)/{print $2}' | tail -1)"
  elapsed_seconds="$(echo "$round_output" | awk -F': ' '/Elapsed seconds/{print $2}' | tail -1)"

  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s\n" \
    "$round" \
    "${throughput:-0.00}" \
    "${created:-0}" \
    "${rate_limited:-0}" \
    "${unexpected:-0}" \
    "${latency_avg:-0.00}" \
    "${latency_p95:-0.00}" \
    "${latency_max:-0.00}" \
    "${elapsed_seconds:-0}" >>"$CSV_PATH"
done

avg_throughput="$(awk -F',' 'NR>1{sum+=$2;count++} END{if(count==0){print "0.00"} else printf "%.2f", sum/count}' "$CSV_PATH")"
avg_p95_latency="$(awk -F',' 'NR>1{sum+=$7;count++} END{if(count==0){print "0.00"} else printf "%.2f", sum/count}' "$CSV_PATH")"
max_p95_latency="$(awk -F',' 'NR>1 && $7>max{max=$7} END{if(max==""){print "0.00"} else printf "%.2f", max}' "$CSV_PATH")"
total_created="$(awk -F',' 'NR>1{sum+=$3} END{print sum+0}' "$CSV_PATH")"
total_rate_limited="$(awk -F',' 'NR>1{sum+=$4} END{print sum+0}' "$CSV_PATH")"
total_unexpected="$(awk -F',' 'NR>1{sum+=$5} END{print sum+0}' "$CSV_PATH")"

{
  echo "# Sustained Benchmark Report"
  echo
  echo "- Generated at (UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- Rounds: $ROUNDS"
  echo "- Total requests per round: $TOTAL_REQUESTS"
  echo "- Concurrency per round: $CONCURRENCY"
  echo "- Average throughput req/s: $avg_throughput"
  echo "- Average p95 latency ms: $avg_p95_latency"
  echo "- Max p95 latency ms: $max_p95_latency"
  echo "- Total HTTP 201 created: $total_created"
  echo "- Total HTTP 429 rate_limited: $total_rate_limited"
  echo "- Total unexpected statuses: $total_unexpected"
  echo
  echo "## Round Details"
  echo
  echo "| Round | Throughput req/s | HTTP 201 | HTTP 429 | Unexpected | Avg ms | P95 ms | Max ms | Elapsed s |"
  echo "|---|---:|---:|---:|---:|---:|---:|---:|---:|"
  awk -F',' 'NR>1 {printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9}' "$CSV_PATH"
  echo
  echo "Raw files:"
  echo "- CSV: \`$CSV_PATH\`"
  echo "- JSON round artifacts: \`$OUTPUT_DIR/round_*.json\`"
} >"$REPORT_PATH"

echo "Sustained benchmark report written to: $REPORT_PATH"
