#!/usr/bin/env bash
# 실험 반복 실행기. 사용법: load/run-experiment.sh <A|B|C|D|E> [repeat=5] [KEY=VALUE ...]
#   예) load/run-experiment.sh B 5 DURATION=3m RATE=100
#       load/run-experiment.sh D 5
#       load/run-experiment.sh E 3 FAIL_MODE=error500 FAIL_RATE=0.5
# 결과: load/results/<exp>-<mode>-<n>.json, <...>.stats.csv(docker stats 5초 샘플), load/results/summary-<exp>.md
set -euo pipefail
cd "$(dirname "$0")/.."
EXP="${1:?A|B|C|D|E}"; REPEAT="${2:-5}"; shift $(( $# >= 2 ? 2 : $# ))
for kv in "$@"; do export "$kv"; done
BASE_URL="${BASE_URL:-http://localhost:8090}"
MYSQL="docker exec -i spike-mysql mysql -uroot -proot spike"
mkdir -p load/results

curl -sf "$BASE_URL/actuator/health" >/dev/null || { echo "앱이 $BASE_URL 에서 응답하지 않습니다. backend 를 먼저 띄우세요 (./gradlew bootRun)."; exit 1; }
curl -sf "${WEBHOOK_URL:-http://localhost:8091}/healthz" >/dev/null || { echo "mock-webhook 이 응답하지 않습니다 (docker compose up -d)."; exit 1; }

sample_stats() { # $1=csv
  echo "ts,name,cpu_pct,mem" > "$1"
  while true; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' spike-mysql spike-redis 2>/dev/null | sed "s/^/$(date +%s),/" >> "$1" || true
    sleep 5
  done
}

run_once() { # $1=script $2=mode $3=n  (환경변수는 이미 export 됨)
  local script="$1" mode="$2" n="$3"
  local out="load/results/${EXP}-${mode}-${n}.json" csv="load/results/${EXP}-${mode}-${n}.stats.csv"
  echo "=== [$EXP] mode=$mode run=$n/$REPEAT → $out"
  sample_stats "$csv" & local sp=$!
  RUN_ID="${EXP}${n}$(date +%s)" OUT_FILE="$out" k6 run --quiet "load/k6/$script" | grep -E '^EXPERIMENT_RESULT|✗|threshold' || true
  kill "$sp" 2>/dev/null || true; wait "$sp" 2>/dev/null || true
}

case "$EXP" in
  A) for n in $(seq 1 "$REPEAT"); do run_once exp-a-detection.js redis "$n"; done ;;
  C) for n in $(seq 1 "$REPEAT"); do run_once exp-c-throughput.js redis "$n"; done ;;
  B)
    for mode in redis db-index db-noindex; do
      if [ "$mode" = db-noindex ]; then $MYSQL < load/sql/drop-fallback-index.sql; fi
      for n in $(seq 1 "$REPEAT"); do MODE="$mode" run_once exp-b-compare.js "$mode" "$n"; done
      if [ "$mode" = db-noindex ]; then $MYSQL < load/sql/add-fallback-index.sql; fi
    done ;;
  D)
    for cd in 0 30; do
      for n in $(seq 1 "$REPEAT"); do COOLDOWN="$cd" run_once exp-d-cooldown.js "cooldown-$cd" "$n"; sleep 35; done   # 다음 run 전에 cooldown 만료
    done ;;
  E)
    mode="${FAIL_MODE:-error500}-${FAIL_RATE:-0.5}"
    for n in $(seq 1 "$REPEAT"); do run_once exp-e-webhook-failure.js "$mode" "$n"; done ;;
  *) echo "unknown experiment $EXP"; exit 1 ;;
esac

node load/summarize.js "$EXP"
