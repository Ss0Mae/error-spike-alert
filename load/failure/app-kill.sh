#!/usr/bin/env bash
# 시나리오 9: 애플리케이션 재시작. webhook 2.5초 지연으로 발송을 느리게 만든 뒤 threshold 1·cooldown 0 으로 알림 300건을 큐에 쌓고 kill -9.
# 재시작 후에도 PENDING 이 그대로 남으면 = @Async 큐 유실 실증. 사용: JAR 경로를 환경변수 APP_JAR 로.
cd "$(dirname "$0")/../.."; source load/failure/lib.sh
APP_JAR="${APP_JAR:-backend/build/libs/error-spike-alert-0.1.0.jar}"
webhook_ctl '{"mode":"slow","failRate":1,"delayMs":2500}'; set_policy 1 0 60; MAX0=$(max_alert_id)
FP="probe-kill-$(date +%s)"
for i in $(seq 1 300); do curl -s -o /dev/null -X POST "$BASE_URL/api/errors" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' -d "{\"environment\":\"PRODUCTION\",\"errorType\":\"KillTest\",\"fingerprint\":\"$FP\",\"message\":\"$i\"}" & [ $((i % 30)) -eq 0 ] && wait; done; wait
sleep 1; echo "before kill: $(status_json)"; echo "alerts before kill:"; alert_status "$MAX0"
PID=$(pgrep -f "error-spike-alert-0.1.0.jar" | head -1); kill -9 "$PID"; echo "killed pid $PID at $(date +%T)"; sleep 2
echo "alerts after kill (DB):"; alert_status "$MAX0"
nohup java -Xms1g -Xmx1g -XX:+UseG1GC -jar "$APP_JAR" > "${APP_LOG:-/tmp/spike-app.log}" 2>&1 &
for i in $(seq 1 40); do sleep 2; curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1 && { echo "app back after $((i*2))s"; break; }; done
sleep 10; echo "alerts 10s after restart (PENDING 잔류 = 유실):"; alert_status "$MAX0"
PENDING_ID=$(docker exec spike-mysql mysql -uroot -proot spike -N -e "SELECT MIN(id) FROM alert_histories WHERE id > $MAX0 AND status='PENDING'" 2>/dev/null)
echo "pending_min_id=$PENDING_ID (manual retry API 는 FAILED 만 받으므로 PENDING 은 운영자 개입 없이는 영원히 남는다)"
webhook_ctl '{"mode":"ok","failRate":0,"delayMs":0}'; set_policy 20 300 60
