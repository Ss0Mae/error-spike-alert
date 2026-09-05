#!/usr/bin/env bash
# 시나리오 8: 비동기 Executor 큐 포화. webhook 을 10초 지연(read timeout 3s × 3회 재시도 ≈ 10초/알림)으로 두고 threshold 1·cooldown 0 으로 이벤트마다 알림 → 큐 200 초과 → FAILED(EXECUTOR_SATURATED).
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
FP="probe-exec-$(date +%s)"; OUT=load/results/failure-executor.csv
webhook_ctl '{"mode":"slow","failRate":1,"delayMs":10000}'; set_policy 1 0 60; MAX0=$(max_alert_id)
load/failure/probe.sh 30 "$FP" "$OUT" & P=$!
sleep 12; echo "status mid-run: $(status_json)"; sleep 18; wait $P
python3 load/failure/analyze.py "$OUT" 0 0 "$(db_count_fp "$FP")" | tee load/results/failure-executor.json
echo "alert status distribution (this run):"; alert_status "$MAX0"
echo "async_executor_queue_size=$(metric async_executor_queue_size) alerts_failed_total=$(metric alerts_failed_total)"
webhook_ctl '{"mode":"ok","failRate":0,"delayMs":0}'; set_policy 20 300 60
