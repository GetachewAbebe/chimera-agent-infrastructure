#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${REDIS_URL:-}" ]]; then
  echo "REDIS_URL is required for redis cluster stress profile"
  exit 1
fi

PORT="${PORT:-8190}"
ROUNDS="${ROUNDS:-3}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-800}"
CONCURRENCY="${CONCURRENCY:-120}"
MAVEN_REPO="${MAVEN_REPO:-/tmp/.m2b}"
OUTPUT_DIR="${OUTPUT_DIR:-artifacts/benchmarks/redis-cluster-$(date -u +%Y%m%dT%H%M%SZ)}"

mkdir -p "$OUTPUT_DIR"
api_log="$OUTPUT_DIR/api.log"

cleanup() {
  if [[ -n "${api_pid:-}" ]]; then
    kill "$api_pid" >/dev/null 2>&1 || true
    wait "$api_pid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

(
  PORT="$PORT" \
  REDIS_URL="$REDIS_URL" \
  CHIMERA_WRITE_RATE_LIMIT_MAX_REQUESTS="2000" \
  CHIMERA_WRITE_RATE_LIMIT_WINDOW_SECONDS="60" \
  CHIMERA_QUEUE_MAX_RETRIES="2" \
  mvn -q -Dmaven.repo.local="$MAVEN_REPO" -DskipTests exec:java \
    -Dexec.mainClass=org.chimera.app.ChimeraApplication
) >"$api_log" 2>&1 &
api_pid=$!

for attempt in $(seq 1 180); do
  if curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null; then
    break
  fi
  sleep 1
  if [[ "$attempt" -eq 180 ]]; then
    echo "API did not become healthy in time. See $api_log"
    exit 1
  fi
done

API_BASE_URL="http://127.0.0.1:${PORT}" \
ROUNDS="$ROUNDS" \
TOTAL_REQUESTS="$TOTAL_REQUESTS" \
CONCURRENCY="$CONCURRENCY" \
OUTPUT_DIR="$OUTPUT_DIR" \
bash scripts/sustained_benchmark.sh

echo "Redis cluster stress profile completed."
echo "Output directory: $OUTPUT_DIR"
echo "API log: $api_log"
