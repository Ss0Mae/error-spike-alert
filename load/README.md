# 부하·장애 실험 실행 가이드

## 사전 준비
```bash
docker compose up -d                       # MySQL 3307 / Redis 6380 / webhook 8091 / Prometheus 9091 / Grafana 3001
(cd backend && ./gradlew bootRun)           # 앱은 호스트 JVM 8090
ROWS=1000000 node load/seed.js && docker exec -i spike-mysql mysql -uroot -proot spike < load/sql/seed-1m.sql   # 실험 B 데이터셋
```

## 실험
| 실험 | 명령 | 주요 env |
|---|---|---|
| A 감지 지연 | `load/run-experiment.sh A 5` | `VUS`(3) `ITERS`(10) `THRESHOLD`(20) |
| B Redis vs DB | `load/run-experiment.sh B 5 DURATION=3m` | `RATE`(100) `DURATION`(3m) `FP_POOL`(50). 모드 redis→db-index→db-noindex 자동 순회. noindex 는 received_at 인덱스 3개를 모두 drop 했다가 복원(1M 행에서 재생성 수십 초) |
| C 100 RPS | `load/run-experiment.sh C 5` | `MEASURE_DURATION`(5m) `WARMUP_DURATION`(1m) `RAMP_STAGE_DURATION`(1m) `SKIP_RAMP`(0) |
| D cooldown | `load/run-experiment.sh D 5` | `RATE`(100) `DURATION`(60s). cooldown 0 → 30 자동 |
| E 채널 장애 | `load/run-experiment.sh E 3 FAIL_MODE=error500 FAIL_RATE=0.5` | `FAIL_MODE`(error500\|rate429\|timeout\|slow) `FAIL_RATE`(0.5) `DELAY_MS`(2000) `RETRY_AFTER`(2) |

공통 env: `BASE_URL`(http://localhost:8090) `API_KEY`(demo-api-key) `ADMIN_TOKEN`(admin-token) `WEBHOOK_URL`(http://localhost:8091) `WEBHOOK_URL_FOR_APP`(http://localhost:8091/webhook) `PROJECT_ID`(1)

단일 실행: `k6 run load/k6/exp-d-cooldown.js -e COOLDOWN=0`

## 결과
- `load/results/<exp>-<mode>-<n>.json` — 스크립트의 `EXPERIMENT_RESULT` 전문
- `load/results/<exp>-<mode>-<n>.stats.csv` — 실행 중 5초마다 `docker stats`(MySQL·Redis CPU/메모리)
- `load/results/summary-<exp>.md` — 모드별 중앙값 ± 표준편차 (`node load/summarize.js <exp>`)
- 실행 계획: `docker exec -i spike-mysql mysql -uroot -proot spike < load/sql/explain-fallback.sql`
- 슬로우 쿼리: `... < load/sql/slow-queries.sql`

## 주의
- 각 run 은 정책 1(프로젝트 1)을 실험 조건으로 PATCH 한다. 실험 후 대시보드에서 원하는 값으로 되돌릴 것.
- D 는 run 사이에 35초 쉬어 cooldown 을 만료시킨다.
- B 의 `db-*` 모드는 `POST /api/system/redis-breaker {forceOpen:true}` 로 fallback 을 강제한다(Redis 는 살아 있음). 실제 Redis 중단은 `docs/experiments/failure-scenarios.md`.
