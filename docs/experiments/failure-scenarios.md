# 장애 실험 시나리오

전제: `docker compose up -d`, 앱 `./gradlew bootRun`(8090), 백그라운드 부하 `k6 run load/k6/exp-b-compare.js -e MODE=redis -e RATE=50 -e DURATION=5m` (수집 오류율·지연 관찰용).
공통 관찰 명령:
```bash
curl -s -H 'X-Admin-Token: admin-token' localhost:8090/api/system/status          # detectionMode, breaker, permits, executor
curl -s localhost:8090/actuator/prometheus | grep -E '^(detection_mode|database_fallback_total|detection_skipped_total|redis_counter_failure_total|alerts_(detected|sent|failed|suppressed)_total|async_executor_queue_size|hikaricp_connections_(active|pending)) '
curl -s localhost:8091/stats                                                      # webhook unique/duplicates/delays
```
기록표 8열: 장애 감지 시간 / fallback 전환 시간 / 유실 이벤트 수 / 중복 알림 수 / 감지 실패 수(SKIPPED) / API 오류율 / 복구 시간 / 전파 여부.
"유실 이벤트 수" = k6 가 202 로 받은 건수 − 해당 구간 `error_events` 증가분(`SELECT COUNT(*) FROM error_events WHERE received_at BETWEEN ...`).

| # | 시나리오 | 재현 명령 | 관찰 지표 | 기대 동작 |
|---|---|---|---|---|
| 1 | Redis 중단 | `docker compose stop redis` → 15 s 후 `docker compose start redis` — `load/failure/redis-stop.sh` (10 RPS 프로브가 요청별 경로·상태를 CSV 로 기록, `analyze.py` 가 전환·복구 시간 계산) | `detection_mode`(0→1), `rate(database_fallback_total[30s])`, `redis_counter_failure_total`, k6 `http_req_failed`, status.redisBreaker.state | 첫 실패 요청부터 DB_FALLBACK. 수집 오류율 0. 복구 후 ≤ 5 s 내 `detection_mode` 0 |
| 2 | Redis 응답 지연 | `docker pause spike-redis; sleep 3; docker unpause spike-redis` (redis:7 은 `DEBUG SLEEP` 이 기본 비활성이라 프로세스를 얼려 무응답을 만든다) — `load/failure/redis-latency.sh` | `redis_counter_failure_total`, `error_counter_duration_seconds{path="REDIS"}` p99 ≤ 0.2 s (타임아웃 상한), breaker OPEN | 200 ms 타임아웃 → breaker OPEN 5 s → fallback → 복귀 |
| 3 | MySQL 응답 지연 | `docker pause spike-mysql; sleep 5; docker unpause spike-mysql` | k6 `http_req_failed`(5xx), HikariCP `connection-timeout` 로그, `hikaricp_connections_pending` | INSERT 실패 = 정직한 5xx. 재시도한 클라이언트가 eventId 로 멱등 복구. Redis 카운터엔 반영 안 됨(과소) |
| 4 | 커넥션 풀 고갈 | `for i in $(seq 10); do docker exec -d spike-mysql mysql -uspike -pspike spike -e 'SELECT SLEEP(30)'; done` + 앱 풀 10 을 `spike.datasource.hikari.maximum-pool-size=4` 로 줄여 재현 | `hikaricp_connections_pending`, `http_req_duration` p99, `detection_skipped_total` | 수집 2 s 타임아웃 후 5xx. fallback 세마포어(8)는 풀보다 크므로 **DB fallback 중이면 COUNT 가 INSERT 커넥션을 잠식** — permits 를 풀 크기 − 2 로 낮춰 재실험해 차이를 기록 |
| 5 | 알림 서버 500 | `curl -X POST localhost:8091/control -d '{"mode":"error500","failRate":1}'` | `alert_retry_total`, `alerts_failed_total`, `GET /api/alerts?status=FAILED`, probe API p95 | 3회 시도(500 ms, 1 s backoff) 후 FAILED. cooldown 키 유지. API p95 불변 |
| 6 | 알림 서버 429 | `... -d '{"mode":"rate429","failRate":1,"retryAfterSec":2}'` | webhook 로그의 도착 간격(≥ 2 s), `alert_retry_total` | Retry-After 준수(≤ 5 s 상한). 최종 FAILED |
| 7 | 알림 서버 타임아웃 | `... -d '{"mode":"timeout","failRate":1}'` | `alert_send_duration_seconds` max ≈ 3 s(read timeout), `async_executor_active_threads` | 3 s × 3회 = 알림당 ≈ 10.5 s 점유. 스레드 8개면 동시 8건 → 큐 증가 |
| 8 | Executor 큐 포화 | 정책 T=1 cooldown=0 + `mode=slow delayMs=10000` + `k6 run load/k6/exp-e-webhook-failure.js -e FAIL_MODE=slow -e FAIL_RATE=1 -e DELAY_MS=10000` | `async_executor_queue_size` → 200, `alerts_failed_total` 증가, failure_reason `EXECUTOR_SATURATED` | 큐 200 초과분은 즉시 FAILED 기록. 수집 API 는 영향 없음(AbortPolicy) |
| 9 | 애플리케이션 재시작 | 8번 조건에서 큐가 찬 순간 `kill -9 $(pgrep -f error-spike-alert)` → 재기동 | `SELECT status, COUNT(*) FROM alert_histories GROUP BY status` — PENDING 잔류 수 = 유실 | **PENDING 잔류 = @Async 유실 실증**. 수동 `POST /api/alerts/{id}/retry` 만 복구. → DESIGN §19 Outbox 근거 |
| 10 | 동일 에러 순간 대량 유입 | `k6 run load/k6/exp-d-cooldown.js -e RATE=1000 -e DURATION=10s -e COOLDOWN=30` | webhook unique(=1 기대), `alerts_suppressed_total`, `cooldown_contention_total`, `docker stats spike-redis` CPU, `error_counter_duration{path=REDIS}` p99 | 알림 1건, 나머지 SUPPRESSED. Redis 단일 키 HINCRBY+HGETALL 60필드 → CPU 관찰(hot key) |

## 기록표 (실측, 2026-09-04 21:11–21:50 KST, `load/failure/*.sh`, 원본 `load/results/failure-*.json`)

프로브(`probe.sh`)는 bash+curl+python 으로 요청마다 프로세스를 띄우므로 지연 열은 ≈ 70 ms 의 프로세스 생성 오버헤드를 포함한다 — 지연은 **변화량**만 보고, 절대값은 k6 실험을 참조.

| # | 시나리오 | 장애 감지 시간 | fallback 전환 시간 | 유실 이벤트 수 | 중복 알림 수 | 감지 실패 수 | API 오류율 | 복구 시간 | 전파 여부 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | Redis 중단 (`compose stop` 15 s) | 첫 요청에서 즉시 (Lettuce 200 ms 타임아웃, `redis_counter_failure_total` +1) | **303 ms** (stop 명령 후 첫 DB_FALLBACK 응답) | **0** (202 응답 153 = DB 행 153) | 0 | 0 (SKIPPED 0, DB_FALLBACK 73건 정상 감지) | **0 %** | **5.7 s** (start 후 breaker 5 s OPEN + HALF_OPEN probe 1회; 실패 probe 3회 = `redis_counter_failure_total` 4) | 없음 — MySQL CPU 변화 없음, 수집 지연 변화 없음 |
| 2 | Redis 응답 지연 (`docker pause` 3 s) | 첫 요청에서 즉시 (200 ms 타임아웃) | **192 ms** (pause 후 첫 DB_FALLBACK 응답) | **0** (107 = 107) | 0 | 0 (DB_FALLBACK 19건 정상 감지) | **0 %** | **2.3 s** (unpause 기준; 첫 실패 시점부터 breaker OPEN 5 s 만료 = 5.2 s) | 없음 |
| 3 | MySQL 응답 지연 (`docker pause` 5 s) | 정지 중 INSERT 가 대기 (max 5,011 ms) | — | **0** (112 = 112) | 0 | 0 | **0 %** — 5 s 는 `socketTimeout=5000` 이내라 대기 후 성공. 5 s 를 넘는 정지는 SocketTimeout → 5xx → 클라이언트 재시도(eventId 멱등) | unpause 직후 (다음 요청 정상) | 없음. Redis 카운터는 대기 후 반영되어 과소 없음 |
| 4 | 커넥션 풀 고갈 (`LOCK TABLES … WRITE` 8 s, 순차 프로브) | 잠긴 INSERT 가 socketTimeout 5 s 에 걸려 5xx (fault 후 297 ms 에 시작된 요청이 5,102 ms 뒤 실패) | — | **0** (타임아웃된 INSERT 는 커밋되지 않음: 202 응답 102 = DB 행 102) | 0 | 0 | 0.97 % (1/103) | 잠금 해제 직후 | 순차 프로브라 풀(10)은 고갈되지 않음(pending 0). **실제 풀 고갈은 실험 C 램프에서 관측**: 200 RPS 부터 active 10 / pending 39, 500 RPS 에서 pending 189, p95 6 → 101 → 378 ms. 이때 fallback 세마포어(8)는 Redis 정상이라 미사용 |
| 5 | 알림 서버 500 (fail 0.5, 실험 E) | 첫 500 응답 즉시 | — | 0 | **0** | — | 수집 probe p95 5.9–8.2 ms (기준 6.0) → **영향 없음** | 재시도 3회 내 87.6 % 성공, 12.5 % 는 FAILED 로 수동 retry 대상 | 없음 (executor 격리) |
| 6 | 알림 서버 429 Retry-After 2 s (실험 E) | 즉시 | — | 0 | **0** | — | probe p95 7.1 ms → 영향 없음 | 발송된 알림 88.4 % 성공, Retry-After 준수로 발송 p50 41.8 s | executor 큐 130 — 재시도 대기가 스레드를 점유 |
| 7 | 알림 서버 타임아웃 (실험 E) | 3 s read timeout | — | 0 | **0** (서버가 응답 못 한 경우라 중복 발생 조건 아님; timeout 후 재시도 도착도 Idempotency-Key 로 dedup) | — | probe p95 15.6 ms → 영향 미미 | 발송된 알림 94.6 % 성공 | executor 큐 169 |
| 8 | Executor 큐 포화 (webhook 10 s 지연, T=1, cooldown 0) | 큐 200 도달 시 `TaskRejectedException` → 즉시 FAILED(EXECUTOR_SATURATED) 기록 (WARN 로그) | — | 0 (이벤트 자체는 저장) | 0 | — | 프로브 오류 0 % (108/108), 지연 변화 없음 | webhook 정상화 후 큐 소진 | **없음 — AbortPolicy 가 수집 스레드를 보호.** 실험 E 에서 큐 200 포화 시 EXECUTOR_SATURATED 5,446건(500 모드 3회 합) 기록, 수집 API p95 5.9–8.2 ms 유지. 프로브 단독 실행(3.6 RPS)에서는 큐 96 까지 |
| 9 | 애플리케이션 재시작 (`kill -9`, 큐에 PENDING 적재) | kill -9 순간. 큐 131 + 실행 중 4 = PENDING 135건 | — | 이벤트 0 (INSERT 는 커밋됨) / **알림 135건 유실** (재시작 4 s 후에도 PENDING 135 그대로, 10 s 후에도 동일) | 0 | — | 재시작 4 s 동안 수집 100 % 실패 (단일 인스턴스) | 앱 4 s. **알림은 자동 복구 없음** — retry API 는 FAILED 만 받으므로 PENDING 은 운영자 SQL 개입 없이는 영원히 남는다 | **@Async 큐 유실 실증** → DESIGN §19 / ADR-005 의 Outbox 폴러 도입 조건 충족 |
| 10 | 동일 에러 순간 대량 유입 (단일 fingerprint 1,000 RPS × 10 s, cooldown 30) | 20건째 즉시 (알림 1건, t+0 s) | — | 0 (10,006 요청 0 % 실패) | **0** (webhook unique 1) | 0 | **0 %**, API p95 4.3 ms / p99 6.8 ms | — | **Redis CPU max 5.5 %** (hot key 영향 미미), MySQL 33.8 %. SUPPRESSED 9,981 = `cooldown_contention_total` 9,981 (SET NX 가 전부 걸러냄) |

## 요약
- Redis 가 죽거나(1) 멈춰도(2) 수집 오류 0 %, 유실 0, 감지는 200–300 ms 안에 DB 경로로 넘어가고 복구 후 2–6 s 안에 돌아온다. 전파 없음.
- MySQL 이 멈추면(3, 4) 정직하게 5xx 를 내고(socketTimeout 5 s), 타임아웃된 INSERT 는 커밋되지 않아 유령 행이 없다. 클라이언트 재시도 + eventId 멱등이 복구 경로.
- 알림 채널 장애(5–8)는 executor 안에 갇혀 수집 API 로 전파되지 않았다(probe p95 ≤ 16 ms). 대신 초당 50건 알림 조건에서는 큐가 차서 대부분이 EXECUTOR_SATURATED 로 기록된다 — cooldown 이 이 양을 막는 것이 전제.
- 프로세스가 죽으면(9) 큐의 알림은 전부 사라진다. 이것이 이 구조의 가장 분명한 한계이고, 다음 단계(Outbox 폴러)의 근거다.
- 단일 fingerprint 1,000 RPS(10) 에서도 알림은 정확히 1건, Redis CPU 5.5 %.

실험 중 발견한 문제(JDBC socketTimeout 부재, DEBUG SLEEP 비활성, 프로브 오버헤드)는 `docs/troubleshooting.md` #7–#8 에 "증상 → 가설 → 검증 → 결론 → 해결" 형식으로 기록했다.
