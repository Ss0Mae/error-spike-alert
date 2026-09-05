# 공통: 정책 설정, 시각, DB 카운트
BASE_URL="${BASE_URL:-http://localhost:8090}"; ADMIN="${ADMIN_TOKEN:-admin-token}"; API_KEY="${API_KEY:-demo-api-key}"
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
set_policy() { # $1=threshold $2=cooldown $3=window
  curl -s -X PATCH "$BASE_URL/api/alert-policies/1" -H "X-Admin-Token: $ADMIN" -H 'Content-Type: application/json' \
    -d "{\"threshold\":$1,\"cooldownSeconds\":$2,\"windowSeconds\":${3:-60},\"enabled\":true,\"webhookUrl\":\"${WEBHOOK_URL_FOR_APP:-http://localhost:8091/webhook}\"}" >/dev/null; }
db_count_fp() { docker exec spike-mysql mysql -uroot -proot spike -N -e "SELECT COUNT(*) FROM error_events WHERE fingerprint='$1'" 2>/dev/null; }
alert_status() { docker exec spike-mysql mysql -uroot -proot spike -e "SELECT status, IFNULL(failure_reason,'') reason, COUNT(*) c FROM alert_histories WHERE id > ${1:-0} GROUP BY status, reason" 2>/dev/null; }
max_alert_id() { docker exec spike-mysql mysql -uroot -proot spike -N -e "SELECT IFNULL(MAX(id),0) FROM alert_histories" 2>/dev/null; }
webhook_ctl() { curl -s -X POST "${WEBHOOK_URL:-http://localhost:8091}/control" -H 'Content-Type: application/json' -d "$1" >/dev/null; }
metric() { curl -s "$BASE_URL/actuator/prometheus" | awk -v m="$1" '$1==m || index($1, m"{")==1 {s+=$2} END{print s+0}'; }
status_json() { curl -s "$BASE_URL/api/system/status" -H "X-Admin-Token: $ADMIN"; }
