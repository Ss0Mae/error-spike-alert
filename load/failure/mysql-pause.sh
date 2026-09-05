#!/usr/bin/env bash
# 시나리오 3: MySQL 응답 지연 (docker pause 5초) → INSERT 실패 = 수집 5xx (정직한 실패) → 복구.
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
FP="probe-mysql-pause-$(date +%s)"; OUT=load/results/failure-mysql-pause.csv
set_policy 1000000 0 60
load/failure/probe.sh 35 "$FP" "$OUT" & P=$!
sleep 8; T_FAULT=$(now_ms); docker pause spike-mysql >/dev/null; sleep 5; docker unpause spike-mysql >/dev/null; T_REC=$(now_ms); echo "mysql paused: $T_FAULT → $T_REC"
wait $P
python3 load/failure/analyze.py "$OUT" "$T_FAULT" "$T_REC" "$(db_count_fp "$FP")" | tee load/results/failure-mysql-pause.json
