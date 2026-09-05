#!/usr/bin/env bash
# 시나리오 2: Redis 응답 지연. redis:7 은 DEBUG 명령이 기본 비활성(enable-debug-command no)이라 `docker pause` 로 3초간 프로세스를 얼려 무응답을 만든다 → 200ms 타임아웃 → breaker OPEN → fallback → unpause 후 5초 내 복귀.
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
FP="probe-redis-latency-$(date +%s)"; OUT=load/results/failure-redis-latency.csv
set_policy 1000000 0 60
load/failure/probe.sh 30 "$FP" "$OUT" & P=$!
sleep 8; T_FAULT=$(now_ms); docker pause spike-redis >/dev/null; sleep 3; docker unpause spike-redis >/dev/null; T_REC=$(now_ms); echo "redis paused 3s: $T_FAULT → $T_REC"
wait $P
python3 load/failure/analyze.py "$OUT" "$T_FAULT" "$T_REC" "$(db_count_fp "$FP")" | tee load/results/failure-redis-latency.json
