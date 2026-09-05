#!/usr/bin/env bash
# 시나리오 4: 커넥션 풀 고갈. 외부 세션이 error_events 를 WRITE LOCK (8초) → 모든 INSERT 대기 → HikariCP pending↑ → connection-timeout(2s) 로 5xx.
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
FP="probe-pool-$(date +%s)"; OUT=load/results/failure-pool-exhaust.csv
set_policy 1000000 0 60
load/failure/probe.sh 35 "$FP" "$OUT" & P=$!
sleep 8; T_FAULT=$(now_ms)
docker exec spike-mysql mysql -uroot -proot spike -e "LOCK TABLES error_events WRITE; SELECT SLEEP(8); UNLOCK TABLES;" >/dev/null 2>&1 &
sleep 4; echo "hikari during lock: active=$(metric hikaricp_connections_active) pending=$(metric hikaricp_connections_pending)"
sleep 5; T_REC=$(now_ms); echo "lock released ≈ $T_REC"
wait $P
python3 load/failure/analyze.py "$OUT" "$T_FAULT" "$T_REC" "$(db_count_fp "$FP")" | tee load/results/failure-pool-exhaust.json
