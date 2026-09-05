#!/usr/bin/env bash
# 시나리오 10: 동일 에러 순간 대량 유입. 단일 fingerprint 1,000 RPS × 10초 (exp-d 스크립트 재사용, cooldown 30 → 알림 1건 기대).
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
( for i in $(seq 1 4); do docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' spike-redis spike-mysql; sleep 3; done ) > load/results/failure-burst.stats.txt &
RUN_ID="burst$(date +%s)" OUT_FILE=load/results/failure-burst.json k6 run --quiet load/k6/exp-d-cooldown.js -e RATE=1000 -e DURATION=10s -e COOLDOWN=30 | grep -E '^EXPERIMENT_RESULT' | cut -c1-600
wait; echo "docker stats during burst:"; cat load/results/failure-burst.stats.txt
set_policy 20 300 60
