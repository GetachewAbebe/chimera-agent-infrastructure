#!/usr/bin/env bash
set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required"
  exit 1
fi

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-dev-tenant-alpha-key}"
TENANT_ID="${TENANT_ID:-tenant-alpha}"
ROLE="${ROLE:-operator}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-120}"
CONCURRENCY="${CONCURRENCY:-24}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-10}"
GOAL_PREFIX="${GOAL_PREFIX:-Load validation campaign}"
OUTPUT_JSON_PATH="${OUTPUT_JSON_PATH:-}"

if [[ "$TOTAL_REQUESTS" -lt 1 ]]; then
  echo "TOTAL_REQUESTS must be >= 1"
  exit 1
fi

if [[ "$CONCURRENCY" -lt 1 ]]; then
  echo "CONCURRENCY must be >= 1"
  exit 1
fi

status_file="$(mktemp)"
cleanup() {
  rm -f "$status_file"
}
trap cleanup EXIT

start_epoch="$(date +%s)"

for index in $(seq 1 "$TOTAL_REQUESTS"); do
  (
    payload="$(printf '{"goal":"%s %s","workerId":"worker-load-%s","requiredResources":["news://ethiopia/fashion/trends"]}' "$GOAL_PREFIX" "$index" "$index")"
    result="$(
      curl -sS -o /dev/null -w "%{http_code},%{time_total}" \
        --max-time "$REQUEST_TIMEOUT_SECONDS" \
        -X POST "$API_BASE_URL/api/campaigns" \
        -H "Content-Type: application/json" \
        -H "X-Api-Key: $API_KEY" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -H "X-Role: $ROLE" \
        -d "$payload" || echo "000,0"
    )"

    status_code="${result%%,*}"
    elapsed_seconds_raw="${result##*,}"
    if [[ ! "$elapsed_seconds_raw" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
      elapsed_seconds_raw="0"
    fi
    elapsed_ms="$(awk "BEGIN { printf \"%.2f\", $elapsed_seconds_raw * 1000 }")"
    printf "%s,%s\n" "$status_code" "$elapsed_ms" >>"$status_file"
  ) &

  while [[ "$(jobs -r | wc -l | tr -d ' ')" -ge "$CONCURRENCY" ]]; do
    wait -n
  done
done
wait

end_epoch="$(date +%s)"
elapsed_seconds=$((end_epoch - start_epoch))
if [[ "$elapsed_seconds" -lt 1 ]]; then
  elapsed_seconds=1
fi

created_count="$(awk -F',' '$1=="201"{count++} END{print count+0}' "$status_file")"
rate_limited_count="$(awk -F',' '$1=="429"{count++} END{print count+0}' "$status_file")"
unexpected_count="$(awk -F',' '$1!="201" && $1!="429"{count++} END{print count+0}' "$status_file")"
throughput_rps="$(awk "BEGIN { printf \"%.2f\", $TOTAL_REQUESTS / $elapsed_seconds }")"
latency_avg_ms="$(awk -F',' '$1=="201"{sum+=$2;count++} END{if(count==0){print "0.00"} else printf "%.2f", sum/count}' "$status_file")"
latency_p50_ms="$(
  awk -F',' '$1=="201"{print $2}' "$status_file" \
    | sort -n \
    | awk 'BEGIN{p=0.50} {a[NR]=$1} END{if(NR==0){print "0.00"; exit} idx=int((NR-1)*p)+1; printf "%.2f", a[idx]}'
)"
latency_p95_ms="$(
  awk -F',' '$1=="201"{print $2}' "$status_file" \
    | sort -n \
    | awk 'BEGIN{p=0.95} {a[NR]=$1} END{if(NR==0){print "0.00"; exit} idx=int((NR-1)*p)+1; printf "%.2f", a[idx]}'
)"
latency_max_ms="$(awk -F',' '$1=="201" && $2>max{max=$2} END{if(max==""){print "0.00"} else printf "%.2f", max}' "$status_file")"

echo "Load Validation Summary"
echo "API base URL: $API_BASE_URL"
echo "Tenant: $TENANT_ID"
echo "Total requests: $TOTAL_REQUESTS"
echo "Concurrency: $CONCURRENCY"
echo "Elapsed seconds: $elapsed_seconds"
echo "Approx throughput req/s: $throughput_rps"
echo "HTTP 201 created: $created_count"
echo "HTTP 429 rate_limited: $rate_limited_count"
echo "Unexpected statuses: $unexpected_count"
echo "Latency avg ms (201): $latency_avg_ms"
echo "Latency p50 ms (201): $latency_p50_ms"
echo "Latency p95 ms (201): $latency_p95_ms"
echo "Latency max ms (201): $latency_max_ms"

if [[ -n "$OUTPUT_JSON_PATH" ]]; then
  mkdir -p "$(dirname "$OUTPUT_JSON_PATH")"
  cat >"$OUTPUT_JSON_PATH" <<EOF
{
  "apiBaseUrl": "$API_BASE_URL",
  "tenantId": "$TENANT_ID",
  "totalRequests": $TOTAL_REQUESTS,
  "concurrency": $CONCURRENCY,
  "elapsedSeconds": $elapsed_seconds,
  "throughputRps": $throughput_rps,
  "http201Created": $created_count,
  "http429RateLimited": $rate_limited_count,
  "unexpectedStatuses": $unexpected_count,
  "latencyAvgMs201": $latency_avg_ms,
  "latencyP50Ms201": $latency_p50_ms,
  "latencyP95Ms201": $latency_p95_ms,
  "latencyMaxMs201": $latency_max_ms
}
EOF
  echo "JSON metrics written to: $OUTPUT_JSON_PATH"
fi

if [[ "$created_count" -eq 0 ]]; then
  echo "No requests were accepted (201)."
  exit 1
fi

if [[ "$unexpected_count" -gt 0 ]]; then
  echo "Unexpected HTTP statuses detected."
  exit 1
fi
