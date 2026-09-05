#!/usr/bin/env bash
# 시나리오 1: Redis 중단 → fallback 전환 → 재기동 → 복귀. 결과 JSON 을 load/results/failure-redis-stop.json 에 기록.
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
FP="probe-redis-stop-$(date +%s)"; OUT=load/results/failure-redis-stop.csv
set_policy 1000000 0 60
load/failure/probe.sh 45 "$FP" "$OUT" & P=$!
sleep 10; T_FAULT=$(now_ms); docker compose stop redis >/dev/null 2>&1; echo "redis stopped at $T_FAULT"
sleep 15; docker compose start redis >/dev/null 2>&1; T_REC=$(now_ms); echo "redis started at $T_REC"
wait $P
python3 load/failure/analyze.py "$OUT" "$T_FAULT" "$T_REC" "$(db_count_fp "$FP")" | tee load/results/failure-redis-stop.json
echo "database_fallback_total=$(metric database_fallback_total) redis_counter_failure_total=$(metric redis_counter_failure_total) detection_skipped_total=$(metric detection_skipped_total)"
