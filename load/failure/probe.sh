#!/usr/bin/env bash
# 장애 실험용 프로브: 약 10 RPS 로 에러를 보내며 각 요청의 시각·상태·감지 경로를 CSV 로 남긴다.
# 사용: load/failure/probe.sh <seconds> <fingerprint> <out.csv>   (BASE_URL, API_KEY 환경변수)
SECS="${1:-40}"; FP="${2:-probe}"; OUT="${3:-load/results/probe.csv}"
BASE_URL="${BASE_URL:-http://localhost:8090}"; API_KEY="${API_KEY:-demo-api-key}"
echo "ts_ms,http,latency_ms,path,result" > "$OUT"
END=$(( $(date +%s) + SECS ))
while [ "$(date +%s)" -lt "$END" ]; do
  T0=$(python3 -c 'import time;print(int(time.time()*1000))')
  R=$(curl -s -m 5 -w '\n%{http_code}' -X POST "$BASE_URL/api/errors" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
      -d "{\"environment\":\"PRODUCTION\",\"errorType\":\"ProbeError\",\"fingerprint\":\"$FP\",\"message\":\"probe\"}")
  T1=$(python3 -c 'import time;print(int(time.time()*1000))')
  CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
  PATH_=$(echo "$BODY" | sed -n 's/.*"path":"\([A-Z_]*\)".*/\1/p'); RES=$(echo "$BODY" | sed -n 's/.*"result":"\([A-Z_]*\)".*/\1/p')
  echo "$T0,$CODE,$((T1-T0)),${PATH_:-NONE},${RES:-NONE}" >> "$OUT"
  sleep 0.1
done
