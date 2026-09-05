# 실측 결과

> 이 문서의 모든 수치는 **실측값**만 적는다. 실험을 하지 않은 칸은 `미측정`. 예상값은 `docs/DESIGN.md` 에만 있다.
> 실행 방법: `load/README.md`. 원본: `load/results/*.json`, 요약: `load/results/summary-*.md`.

## 실측 조건
| 항목 | 값 |
|---|---|
| 측정 일시 | 2026-09-04 18:43–19:50 KST (A → D → B → C → E → 장애 시나리오 순, 같은 앱 프로세스·같은 컨테이너) |
| 환경 | `environment.md` 대로. 앱은 호스트 JVM(`-Xms1g -Xmx1g -XX:+UseG1GC`), MySQL·Redis·webhook·Prometheus 는 Docker Desktop |
| 반복 횟수 | A 5회, D 5회×2조건, B 5회×3모드(1분), C 5회(측정 1분) + 1회(스펙 그대로 5분+램프), E 500 3회·429/timeout/slow 각 1회 |
| 데이터셋 | `error_events` 1,000,000행 시드(테이블 563 MB, 인덱스 278 MB) + 실험 중 유입분 |
| 실험 B 기간 단축 | 스펙은 3분×5회지만 1분×5회로 단축(총 소요 시간). 램프·E 도 결과표에 실제 길이를 적음 |

## 실험 B — Redis 경로 vs DB fallback (100 RPS, **1분**, n=5, 중앙값 ± 표준편차)

조건: 동일 앱 프로세스, `error_events` 1M 행 + 유입분, fingerprint 풀 50개(→ fingerprint 당 2 events/s), 1.5 KB stackTrace, 정책 T=1,000,000(알림 없음). `db-*` 는 `POST /api/system/redis-breaker {forceOpen:true}` 로 경로만 강제(Redis 는 살아 있음). `db-noindex` 는 received_at 복합 인덱스 3개 DROP. Prometheus 행은 각 모드 3번째 실행의 1분 창(`load/prom-window.js`).

| 지표 | Redis 정상 | DB fallback (인덱스 有) | DB fallback (인덱스 無) | 변화 (Redis → 인덱스 有 / 無) |
|---|---:|---:|---:|---:|
| 집계 평균시간 (`error_counter_duration` avg) | 0.51 ms | 0.19 ms (1 s 로컬 캐시 히트 ≈ 50 %) | 80 ms | −63 % / +157× |
| 집계 p95 | 0.98 ms | 0.96 ms | 1,018 ms (= 쿼리 타임아웃 1 s) | ≈ / +1,000× |
| fallback COUNT 쿼리 자체 (avg / p95) | — | 0.55 / 0.99 ms | 1,003 / 1,069 ms | — |
| API 평균 응답시간 (k6) | 4.97 ms ± 1.25 | 4.97 ms ± 0.32 | 102 ms ± 6.3 | ≈ / +20× |
| API p50 | 3.97 ms ± 0.44 | 4.10 ms ± 0.19 | 9.07 ms ± 1.43 | +3 % / +128 % |
| API p95 | **9.31 ms ± 4.08** | **7.97 ms ± 1.14** | **1,009 ms ± 0.8** | −14 % / +108× |
| API p99 | 25.3 ms ± 18.3 | 29.3 ms ± 5.2 | 1,028 ms ± 11 | +16 % / +41× |
| API max | 64.7 ms | 97.5 ms | 1,537 ms | — |
| TPS (k6 실제) | 100.0 | 100.0 | 98.5 | ≈ / −1.5 % |
| MySQL CPU avg / max (`docker stats`, 코어 100 % 기준) | 10.0 % / 12.6 % | 13.5 % / 29.1 % | **772 % / 791 %** (≈ 7.7 코어) | +35 % / +77× |
| Redis CPU avg | 1.48 % | 0.46 % | 0.38 % | — |
| rows examined (EXPLAIN ANALYZE, 아래) | — | 20 (60 s 창) | 1,000,043 | — |
| HikariCP active max / pending max | 3 / 0 | 1 / 0 | 10 / 6 | — |
| fallback 전환 시간 | — | 강제 OPEN (전환 없음). 실제 중단 실험은 `failure-scenarios.md` #1 | — | — |
| 서버 오류율 (k6 `http_req_failed`) | 0 % | 0 % | **0 %** | — |
| 감지 SKIPPED 건수 (60 s, 6,005 요청 중) | 0 | 0 | **5,911 (98 %)** | — |
| `database_fallback_total` Δ | 0 | 5,963 | 5,911 | — |

읽는 법
- 100 RPS·단일 인스턴스에서는 **인덱스 있는 DB fallback 이 Redis 경로와 지연이 같다**(p95 8.0 vs 9.3 ms). fallback 의 1초 로컬 캐시가 같은 fingerprint 의 두 번째 이벤트부터 DB 를 치지 않기 때문이다(집계 avg 0.19 ms < Redis 왕복 0.51 ms). 차이는 MySQL CPU(+35 %)와 "다중 인스턴스에서 캐시 구간 동안 과소 집계" 라는 정확도 조건에 있다. Redis 경로의 p95 편차(±4.08)는 5회 중 1회의 max 65 ms 스파이크(GC 추정, 미확인) 때문이다.
- 인덱스가 없으면 COUNT 하나가 1M 행을 읽어 **매 쿼리가 1 s 타임아웃에 걸린다.** 세마포어 8 개가 항상 점유되어 초당 약 8 건은 1 s 를 기다리고(p95 = 1,009 ms) 나머지 92 % 는 즉시 SKIPPED. 그래도 INSERT 는 계속되어 오류율 0 %, HikariCP pending 최대 6 — "감지를 포기하고 수집을 지킨다" 는 degraded 설계가 실제로 작동한 결과다. 반대로 MySQL 은 7.7 코어를 태웠다. 이 상태가 길어지면 다른 서비스가 같은 DB 를 쓰는 환경에서는 전파된다 → 타임아웃을 200 ms 로 줄이거나 permits 를 낮추는 것이 다음 조정 포인트(p95 가 곧 타임아웃 값이 된다).

실행 계획 (인덱스 有 / 無, `load/results/explain-with-index.txt`, `explain-without-index.txt`, 1,000,000행)

| | 인덱스 有 (`idx_ee_project_env_fp_received`) | 인덱스 無 (received_at 복합 인덱스 3개 DROP) |
|---|---|---|
| 60 s 창 (대상 20행) 실행 계획 | `Covering index range scan on error_events using idx_ee_project_env_fp_received` | `Index lookup on error_events using uk_ee_project_event (project_id=1)` → 100만 행 필터 |
| 60 s 창 actual time | **1.1 ms** (cost 6.46, rows 20) | **696 ms** (cost 14008) |
| 60 s 창 `Handler_read_next` | 20 | 1,000,043 |
| 24 h 창 (대상 4,956행) actual time | 4.9 ms | 416 ms |
| 24 h 창 `Handler_read_next` | 4,956 | 1,000,043 |
| 인덱스 DROP / ADD 소요 (1M 행) | — | DROP 1.8 s / ADD 2.9 s |

주의: fallback 인덱스 하나만 지우면 옵티마이저가 `idx_ee_project_env_received` 로 우회해(range + fingerprint 필터) full scan 이 재현되지 않는다. 그래서 "인덱스 無" 는 시간축 인덱스 3개를 모두 지운 상태다(`docs/troubleshooting.md` #2).

## 실험 D — cooldown 효과 (W=60 s, T=20, 100 RPS × 60 s, 단일 fingerprint, n=5)

| 지표 | Cooldown 전 (0 s) | Cooldown 후 (30 s) | 개선 |
|---|---:|---:|---:|
| 발송 시도 (`alerts_detected_total` Δ) | 5,978 ± 10.5 | 2 ± 0 | −99.97 % |
| 실제 알림 (webhook unique) | 5,978 ± 10.5 | 2 ± 0 | −99.97 % |
| 중복 알림 (webhook duplicates) | 0 | 0 | — |
| 차단된 알림 (`alerts_suppressed_total` Δ) | 4 ± 10.7 (ms 슬롯 dedup_key 충돌: cooldown 0 이면 슬롯 = epoch ms 라 같은 ms 에 두 건이 겹치면 DB UNIQUE 가 막음) | 5,980 ± 0.4 | — |
| cooldown 키 획득 충돌 (`cooldown_contention_total` Δ) | 0 (SET NX 를 타지 않음) | 5,980 ± 0.4 | — |
| cooldown 만료 후 재알림 (detectedAt 목록, run 1) | 6,000건 전부 | **t+0.2 s (count 20), t+30.2 s (count 3,021)** — 30 s 만료 직후 정확히 1건 재알림 | — |
| 알림 p95 (webhook detectionDelayMs) | 15 ms (run 1 은 471 ms — 알림 5,953건이 executor 큐에 밀린 첫 실행) | 11 ms ± 1.3 | — |
| 수집 API p95 (k6) | 8.1 ms ± 19.4 (run 1: 54.9 ms) | 4.34 ms ± 0.27 | −46 % |
| 수집 API p99 (k6) | 16.3 ms ± 28.3 | 5.16 ms ± 0.50 | −68 % |
| 요청 수 / 실패율 | 6,006 / 0 % | 6,006 / 0 % | — |

알림 감소율(%) = (5,978 − 2) / 5,978 × 100 = **99.97 %**. cooldown 없는 조건의 첫 실행(run 1)은 5,953건의 알림 INSERT·발송이 수집 스레드와 경쟁해 API p95 54.9 ms, 감지 지연 p95 471 ms 를 기록했고 이후 실행은 JIT 워밍업 뒤라 8 ms/15 ms 수준이었다. 중앙값을 대표값으로 쓰되 편차를 그대로 남긴다.

## 실험 A — 감지 지연 (W=60 s, T=20, cooldown 30 s, VUS=3 × ITERS=10, n=5)

| 지표 | 값 |
|---|---:|
| 평균 감지 지연 (webhook 수신 − 20번째 이벤트 receivedAt) | 14.2 ms ± 1.6 |
| p50 | 13.0 ms ± 1.9 |
| p95 | 20.0 ms ± 2.6 |
| p99 | 21.0 ms ± 7.4 |
| 최대 | 21.0 ms ± 7.4 |
| 비동기 큐 대기 (webhook dispatchDelayMs = 수신 − detectedAt) p95 | 20.0 ms (detectedAt = trigger 이벤트 receivedAt 이라 감지 지연과 동일) |
| 알림 발송 시간 (`alert_send_duration`, 격리 재실행 — 재시작 1.5 분 뒤 콜드 상태, `metrics-delta.js` 로 스냅샷 차이 계산) | p50 2.1 ms / avg 4.1 ms / p95 19.6 ms (같은 실행의 감지 지연 p95 54.5 ms → 큐 대기 ≈ 감지 − 발송 ≈ 35 ms 콜드, 워밍업 후 실행에서는 감지 지연 p95 20 ms) |
| 20번째 요청 API 지연 p95 | 10.6 ms ± 8.0 (알림 이력 INSERT + dispatch 비용이 얹힘; 일반 요청 p95 5.9 ms) |
| TRIGGERED / NOT_TRIGGERED 비율 | 30/30 iteration 모두 20번째 이벤트에서 TRIGGERED. webhook unique 28 ± 0.5 는 summary 시점에 마지막 알림 1–2건이 아직 비행 중이었기 때문(중복 0) |
| 요청 수 / 실패율 | 604 / 0 % |

## 실험 C — 100 RPS 처리 + 램프 (200 / 500 / 1,000 RPS)

두 가지 실행: (1) 단축 5회 — 워밍업 30 s + 측정 1 분(n=5, 중앙값 ± 표준편차), (2) 스펙 그대로 1회 — 워밍업 1 분 + 측정 5 분(30,000 요청) + 램프 200/500/1,000 RPS 각 1 분. fingerprint 풀 50, 1.5 KB stackTrace, 정책 T=1,000,000. Prometheus 행은 각 구간의 1분 창(측정 구간은 5분 창).

| 지표 | 100 RPS (단축 5회) | 100 RPS (5 분, 1회) | 200 RPS | 500 RPS | 1,000 RPS |
|---|---:|---:|---:|---:|---:|
| 수집 API p50 (k6) | — | 3.84 ms | 2.86 ms | 4.47 ms | 316 ms |
| 수집 API p95 (k6) | **7.20 ms ± 2.07** | **6.04 ms** | 71.1 ms | 762 ms | 2,088 ms |
| p99 (k6) | 24.9 ms ± 9.9 | 23.8 ms | 859 ms | 1,214 ms | 2,934 ms |
| 서버 측 `/api/errors` p95 (Prometheus) | 6.9–12.5 ms | 5.96 ms | 101 ms | 378 ms | 468 ms |
| 처리량 (실제 수신, `errors_received` Δ/60 s) | 100.0 | 100.0 | 197.5 | 489.7 | 918 (k6 dropped iterations 5,619 — 부하 생성기 VU 상한) |
| 실패율 | 0 % | 0 % | 0 % | 0 % | 0.08 % |
| DB INSERT 시간 (ingestion − counter, avg; **커넥션 대기 포함**) | 3.3–3.8 ms | 3.2 ms | 10.0 ms | 31.2 ms | 72.7 ms |
| Redis 집계 시간 (`error_counter_duration{path=REDIS}` avg / p95) | 0.4–0.5 / 0.97 ms | 0.40 / 0.95 ms | 0.32 / 0.95 ms | 0.38 / 0.99 ms | 0.58 / 1.27 ms |
| HikariCP active max / pending max | 1–2 / 0 | 1 / 0 | **10 / 39** | 10 / 189 | 10 / 189 |
| 비동기 Executor 큐 max | 0 | 0 | 0 | 0 | 0 |
| MySQL CPU avg (`docker stats`) | 8.8 % ± 0.7 | 미측정 (직접 실행이라 샘플러 미가동) | 미측정 | 미측정 | 미측정 |
| Redis CPU avg | 1.4 % ± 0.2 | 미측정 | 미측정 | 미측정 | 미측정 |

**급격한 성능 저하 지점: 100 → 200 RPS 사이.** 200 RPS 에서 HikariCP 커넥션 10개가 전부 사용 중이 되고(pending 39) 서버 측 p95 가 6 ms → 101 ms 로 뛴다. 500 RPS 에서는 Tomcat 스레드 대부분이 커넥션을 기다린다(pending 189). Redis 집계 시간은 1,000 RPS 에서도 p95 1.3 ms 로 변하지 않았으므로 병목은 **MySQL INSERT 경로(커넥션 풀 10 + 커밋마다 fsync)** 다. 이 값이 §19 "이벤트 비동기 저장 → Kafka" 확장의 도입 조건이다. 주의: k6·앱·Docker 가 같은 10코어 머신에서 돌았으므로 1,000 RPS 구간은 CPU 경쟁이 섞여 있다(절대값은 보수적).

MySQL 측 문장 시간(`performance_schema.events_statements_summary_by_digest`, 전체 실험 누적, 실험 C 종료 시점): `INSERT INTO error_events` 334,554건 avg **0.37 ms** / max 850 ms, `INSERT INTO alert_histories` 33,839건 avg 0.10 ms, fallback `SELECT COUNT(*)` 12,439건 avg 193 ms(인덱스 無 구간 포함) / max 2,068 ms. `COMMIT` 530,768건 avg **2.49 ms** / max 1,016 ms. 즉 앱에서 본 INSERT 3.2 ms ≈ 문장 0.37 ms + 커밋(fsync, `innodb_flush_log_at_trx_commit=1`) 2.49 ms + JDBC 왕복이고, 램프 구간의 증가분은 커넥션 대기다. 커밋 fsync 가 쓰기 경로의 상한을 정한다 → 배치 INSERT(커밋 묶기)가 §19 확장의 첫 후보인 이유.

## 실험 E — 알림 서버 장애 (T=5, cooldown 0, flood 50 RPS × 60 s + probe 5 RPS)

조건: fingerprint 20개 풀, **T=5·cooldown 0 이라 이벤트 거의 전부가 알림**(≈ 2,900–3,000 알림/분 = 초당 50건). 이는 채널 장애 시 executor·재시도가 어떻게 무너지는지 보려는 극단 조건이고, 실제 운영에서는 cooldown 이 이 양을 §D 처럼 2건/분으로 줄인다. 500 모드는 n=3(중앙값), 나머지는 n=1. "발송된 알림" = SENT + RETRY_EXHAUSTED(= executor 에 들어가 실제로 시도된 것). 첫 실행 세트는 JDBC 소켓 행(`troubleshooting.md` #7)으로 무효 처리하고 `socketTimeout=5000` 적용 후 재실행한 값이다.

| 지표 | 500 (fail 0.5, n=3) | 429 Retry-After 2 s (fail 0.5) | timeout (fail 0.5) | slow 2 s (fail 1.0) |
|---|---:|---:|---:|---:|
| 감지된 알림 (`alerts_detected` Δ) | 3,000 | 2,921 | 2,908 | 2,921 |
| 재시도 전 성공률 (webhook ok / 도착) | 51.1 % (설정 50 %) | 47.7 % | 49.1 % | 2 s 뒤 전부 2xx (mock 은 outcome=slow 로 집계) |
| 발송된 알림 중 최종 성공률 (SENT / (SENT+RETRY_EXHAUSTED)) | **87.6 %** (3,073 / 3,506; 이론값 1−0.5³ = 87.5 %) | 88.4 % (343 / 388) | 94.6 % (334 / 353) | 100 % (428 / 428, 재시도 0) |
| 감지 대비 최종 성공률 (SENT / detected) | 34.0 % | 7.1 % | 5.4 % | 9.6 % |
| executor 거부 (`EXECUTOR_SATURATED`) | 5,446 / 8,952 (61 %) | 2,533 (87 %) | 2,555 (88 %) | 2,493 (85 %) |
| 평균 재시도 횟수 (`alert_retry` Δ / 발송된 알림) | 0.72 (SENT 평균 attempt 1.54) | 0.46 | 0.39 | 0 |
| 최종 실패 (RETRY_EXHAUSTED) | 433 (3회 연속 500 = 12.5 % 이론값과 일치) | 45 | 19 (`IO: Could not ret…` = read timeout) | 0 |
| 중복 발송 (webhook duplicates, Idempotency-Key 기준) | **0** | 0 | 0 | (재시도 없음 → 0) |
| 발송 완료 p50 / p95 (webhook dispatchDelayMs) | 13.8 s / 14.0 s | 41.8 s / 59.6 s | 36.9 s / 65.5 s | — |
| Executor 큐 (run 종료 시점 / 최대 200) | 0 (다 비움) | 130 | 169 | 140 |
| 수집 API p95 — probe (장애 없을 때 6.0 ms) | **5.9–8.2 ms** | 7.1 ms | 15.6 ms | 4.7 ms |
| flood 요청 p95 (알림 이력 INSERT + dispatch 포함) | 12.1–14.6 ms | 16.7 ms | 24.7 ms | 9.6 ms |
| k6 실패율 | 0 % | 0 % | 0 % | 0 % |

읽는 법
- 재시도는 설계대로 동작했다: 500 이 50 % 면 3회 시도로 87.5 % 가 성공해야 하고 실측 87.6 %. 429 는 `Retry-After` 2 s 를 지켜 재시도했고(도착 간격 로그), timeout 은 3 s read timeout 후 재시도했다. **중복 발송은 네 조건 모두 0** — 서버가 처리했는데 클라이언트가 실패로 판정하는 경우(timeout 모드)에도 `Idempotency-Key` 로 걸러졌다.
- 그러나 초당 50건의 알림 앞에서는 재시도가 오히려 executor 를 잠식한다: 429 는 재시도마다 2 s 를 자고, timeout 은 알림당 최대 3 × 3 s 를 붙잡아 8 스레드로는 초당 1–3건밖에 못 보낸다. 큐 200 이 차면 `AbortPolicy` 가 나머지를 즉시 FAILED(EXECUTOR_SATURATED) 로 기록했고 **수집 API p95 는 5–16 ms 로 유지됐다** — 채널 장애가 수집으로 전파되지 않았다는 것이 이 실험의 핵심이다.
- 감지 대비 최종 성공률이 5–34 % 로 낮은 것은 cooldown 0 이라는 조건의 결과다. cooldown 30 s 면 같은 fingerprint 20개에서 분당 알림이 ≤ 40건이라 executor 는 비지 않는다. 이 값이 §19 "알림 DLQ / Outbox" 의 도입 조건이다.

## 장애 시나리오 10종

기록표는 `failure-scenarios.md`. 핵심만: Redis 중단 → fallback 전환 303 ms·복귀 5.7 s·오류 0·유실 0 / Redis pause 3 s → 전환 192 ms·복귀 2.3 s / MySQL pause 5 s → 오류 0(최대 5.0 s 대기) / 테이블 락 8 s → socketTimeout 5 s 로 5xx 1건, 유령 행 0 / 알림 채널 장애 → 수집 probe p95 ≤ 16 ms / kill -9 → PENDING 135건 100 % 유실 / 단일 fingerprint 1,000 RPS → 알림 1건, Redis CPU 5.5 %.

## 이력서 문장 (실측값으로만 채운다)

> MySQL 이력 저장과 Redis 실시간 집계를 결합한 에러 급증 감지 시스템을 구현했습니다.
> Redis 장애 시 timestamp 복합 인덱스를 활용한 DB fallback 을 적용하여 감지 기능을 유지했고,
> 원자적 cooldown 을 통해 초당 100건의 에러 유입 상황에서 중복 알림을 **A=5,978** 건에서 **B=2** 건으로 감소시켰습니다(감소율 99.97 %, webhook 중복 수신 0건).
> 또한 Redis 경로와 DB fallback 경로의 p95 를 각각 **C=9.3** ms 와 **D=8.0** ms(시간축 인덱스가 없으면 1,009 ms)로 측정해 성능과 가용성의 트레이드오프를 검증했습니다.
