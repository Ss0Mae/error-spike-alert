# 트러블슈팅 기록

개발·측정 중 실제로 부딪힌 문제를 "증상 → 가설 → 검증 → 결론 → 해결" 틀로 남긴다. 검증 칸의 명령·수치는 실제 실행한 것만 적는다.

## 1. webhook 감지 지연이 항상 NaN

| 단계 | 내용 |
|---|---|
| **증상** | Mock Webhook `/stats` 의 `detectionDelayMs.count` 가 0. 로그에 `detectionDelayMs=NaN`. 알림은 정상 수신(SENT)됐는데 실험 A 의 핵심 지표가 비어 있음 |
| **가설** | 1) V8 `Date.parse` 가 6자리 소수초(`…54.316359Z`)를 못 읽는다 2) 페이로드 필드명 불일치(`triggerEventReceivedAt`) 3) 앱이 `Instant` 를 ISO 문자열이 아닌 숫자로 직렬화 |
| **검증** | `node -e 'Date.parse("2026-09-04T09:39:54.316359Z")'` → `1788514794316` (1 기각). `WebhookPayload` record 필드명은 일치(2 기각). `AsyncConfig.webhookRestClient` 가 `RestClient.builder()` 로 빈을 만듦 → Spring Boot 가 구성한 ObjectMapper(`WRITE_DATES_AS_TIMESTAMPS=false`)를 쓰지 않고 Jackson 기본값(epoch 초 숫자)으로 직렬화 |
| **결론** | 원인 = 3). REST API 응답은 Boot MVC 컨버터를 타서 ISO 문자열이었지만, 알림 클라이언트만 별도 ObjectMapper 를 써서 `1788514794.316359` 같은 숫자를 보냈고 `Date.parse(숫자)` 가 NaN |
| **해결** | Boot 가 제공하는 `RestClient.Builder` 를 주입받아 빌드(`builder.requestFactory(f).build()`). 재배포 후 `detectionDelayMs=47` 확인. 교훈: HTTP 클라이언트를 직접 `builder()` 로 만들면 Boot 의 Jackson 설정이 빠진다 |

## 2. "인덱스 없음" 기준선이 full scan 이 아니었다

| 단계 | 내용 |
|---|---|
| **증상** | 실험 B 의 `db-noindex` 를 위해 fallback 인덱스 `idx_ee_project_env_fp_received` 하나만 지웠더니 EXPLAIN 이 여전히 range scan |
| **가설** | 1) DROP 이 적용되지 않았다 2) 옵티마이저가 다른 복합 인덱스로 우회한다 |
| **검증** | `EXPLAIN ANALYZE … WHERE project_id=1 AND environment='PRODUCTION' AND fingerprint=? AND received_at>=?` → `Index range scan on error_events using idx_ee_project_env_received` + fingerprint 필터. 접두사 `(project_id, environment, received_at)` 가 범위 조건까지 커버해서 선택됨 |
| **결론** | 원인 = 2). 인덱스 하나를 지워도 접두사가 겹치는 다른 인덱스가 대신 쓰인다. "인덱스가 없을 때" 를 재현하려면 시간축 인덱스를 전부 지워야 한다 |
| **해결** | `drop-fallback-index.sql` 이 received_at 복합 인덱스 3개를 모두 지우고 `add-fallback-index.sql` 이 복원. 그 상태의 EXPLAIN 은 `Index lookup on error_events using uk_ee_project_event (project_id=1)` 로 100만 행 전체를 읽음(`Handler_read_next 1,000,043`, 696 ms). 인덱스 有: `Covering index range scan on idx_ee_project_env_fp_received`, `Handler_read_next 20`, 1.1 ms |

## 3. Testcontainers 가 Docker 를 못 찾음

| 단계 | 내용 |
|---|---|
| **증상** | `./gradlew test` 에서 "Could not find a valid Docker environment". `docker ps` 는 정상 |
| **가설** | 1) DOCKER_HOST 미설정 2) docker-java 가 요청하는 API 버전을 Docker 29 데몬이 거부 |
| **검증** | Docker 데몬 29.6.1. docker-java 기본 API 버전 1.32 요청 → 데몬 최소 지원 버전 미만으로 거부 |
| **결론** | 원인 = 2) |
| **해결** | `build.gradle` test 태스크에 `systemProperty 'api.version', '1.44'`. 이후 19개 IT 28초 통과 |

## 4. 첫 알림의 감지 지연만 450 ms

| 단계 | 내용 |
|---|---|
| **증상** | 스모크 테스트 첫 알림 `alert_detection_delay_seconds_sum 0.449967`, 이후 알림은 13–21 ms |
| **가설** | 1) executor 스레드 생성·JIT 워밍업 2) RestClient 첫 연결 수립 3) webhook 컨테이너 첫 요청 |
| **검증** | 두 번째 앱 기동 직후 첫 알림 47 ms, 실험 A(워밍업 후) p99 21 ms. `poolSize` 가 첫 알림 전 0→1 |
| **결론** | 워밍업 효과(1·2 복합). 측정에는 포함하지 않되 운영에서는 "첫 알림은 느리다" 를 알고 있어야 함 |
| **해결** | k6 실험은 워밍업 구간을 두고 측정 구간만 집계. 필요하면 `prestartAllCoreThreads` 로 스레드를 미리 띄울 수 있으나 47 ms 수준이라 적용하지 않음 |

## 5. 인덱스 있는 DB fallback 이 Redis 보다 빠르게 나왔다

| 단계 | 내용 |
|---|---|
| **증상** | 실험 B 100 RPS 에서 API p95: Redis 9.31 ms, DB fallback(인덱스 有) 7.97 ms. "Redis 가 더 빠르다" 는 전제와 반대 |
| **가설** | 1) Redis 경로에 GC 스파이크가 섞였다 2) fallback 의 1초 로컬 캐시가 DB 왕복을 대부분 없앤다 3) 측정 오류(경로가 실제로 안 바뀜) |
| **검증** | `counter_path_db.avg = 1.00`(db-index), `database_fallback_total Δ 5,963/5,963` → 경로는 확실히 DB(3 기각). Prometheus 1분 창: `error_counter_duration` avg Redis 0.51 ms vs DB 0.19 ms, `database_fallback_duration` avg 0.55 ms — 즉 DB 쿼리 자체는 Redis 왕복과 비슷한데 캐시 히트가 절반이라 평균이 낮다(2 지지). Redis 5회 중 1회 max 64.7 ms, p95 편차 ±4.08(1 도 일부 기여) |
| **결론** | 원인 = 2)(+1). fingerprint 50개에 100 RPS 면 fingerprint 당 2 events/s → 1초 캐시가 절반을 흡수. Redis 는 매 이벤트 왕복. 단일 인스턴스·100 RPS 에서는 지연 차이가 없고, 차이는 MySQL CPU(10 % → 13.5 %)와 다중 인스턴스에서의 과소 집계 조건에 있다 |
| **해결** | 결과를 그대로 기록(README 이력서 문장의 C/D 값도 이 값). "Redis 가 항상 빠르다" 대신 "Redis 는 DB CPU 를 쓰지 않고 인스턴스 간 정확한 집계를 준다" 로 주장을 정정. 지연 차이를 보려면 fingerprint 풀을 늘리거나(캐시 미스) 다중 인스턴스에서 측정해야 함 |

## 6. 인덱스 없는 fallback 의 p95 가 정확히 1,009 ms

| 단계 | 내용 |
|---|---|
| **증상** | db-noindex 모드 API p95 1,009 ms ± 0.8, p99 1,028 ms — 5회 모두 거의 같은 값 |
| **가설** | 1) DB 가 포화되어 모든 요청이 느리다 2) 쿼리 타임아웃(`MAX_EXECUTION_TIME(1000)`)에 걸린 요청만 1 s 를 기다리고 나머지는 빠르다 |
| **검증** | p50 9.07 ms(빠름), `detection_skipped Δ 5,911/6,005`(98 % 즉시 스킵), `database_fallback_duration` avg 1,003 ms(전부 타임아웃), HikariCP active max 10·pending 6, 오류율 0 % |
| **결론** | 원인 = 2). 세마포어 8개를 쥔 요청은 1 s 타임아웃까지 기다리고(초당 ≈ 8건 = 8 %), 나머지는 tryAcquire 실패로 SKIPPED. 그래서 p95 ≈ 타임아웃 값이 된다. DB 는 7.7 코어를 썼지만 INSERT 는 살아 있었다 |
| **해결** | 설계대로 동작한 것이므로 코드 변경 없음. 운영 조정 포인트로 기록: 쿼리 타임아웃이 곧 fallback 중 API p95 상한이므로 지연 예산에 맞춰 200 ms 수준으로 낮추는 것을 검토. 근본 해결은 인덱스(있으면 0.99 ms) |

## 7. 장애 실험 중 앱이 17분 단위로만 깨어남 — 타임아웃 없는 JDBC 소켓 읽기

| 단계 | 내용 |
|---|---|
| **증상** | 실험 E(알림 서버 500, 50 RPS) 3회차가 80초가 아니라 **90분** 걸림. k6 요청 최대 지연 1,069,149 ms(17.8분), 실행 중 RPS 0.53. 앱 로그가 19:37 → 19:53 → 20:10 → 20:28 → 20:44 → 21:01 처럼 **약 17분 간격으로만** 쏟아짐. webhook `dispatchDelayMs` p95 2,009 s |
| **가설** | 1) Executor 큐 포화로 요청 스레드가 막힘(AbortPolicy 라 아님) 2) webhook HTTP 클라이언트 read timeout(3 s) 미적용 3) MySQL 이 멈춤 4) MySQL JDBC 소켓 읽기에 타임아웃이 없어 Docker 포트 포워딩이 끊긴 연결에서 TCP 재전송 포기까지 대기 |
| **검증** | `jstack 95206`(`load/results/failure-jdbc-stall-jstack.txt`): Tomcat `http-nio-8090-exec-31`(elapsed 2,251 s) 이 `com.mysql.cj.protocol.FullReadInputStream.readFully` ← `ConnectionImpl.setAutoCommit` ← `ErrorIngestionService.ingest` 에서 RUNNABLE 로 정지. `alert-*` 스레드는 `NioSocketImpl.timedRead`(타임아웃 있음) ← `HttpURLConnection.getResponseCode`(2 기각). MySQL `Threads_connected 11`, `Aborted_clients 0`, `Max_used_connections 12` — 서버는 정상(3 기각). JDBC URL 에 `socketTimeout` 없음(기본 0 = 무한). 17분 주기는 TCP 재전송 포기 시간과 일치 |
| **결론** | 원인 = 4). Docker Desktop 포트 포워딩(호스트 JVM → 컨테이너 MySQL)이 부하 중 일부 연결을 조용히 끊었고, 드라이버가 응답을 무한 대기. HikariCP `connection-timeout` 은 풀에서 커넥션을 "얻는" 시간만 제한하므로 이미 얻은 커넥션의 읽기는 보호하지 않는다. 요청 스레드가 하나씩 17분씩 잠기면서 k6 VU 가 고갈되어 실험 전체가 늘어졌다 |
| **해결** | JDBC URL 에 `socketTimeout=5000&tcpKeepAlive=true` 추가(INSERT ≈ 3 ms, fallback COUNT ≤ 1 s 이므로 5 s 는 충분히 큰 상한). 초과 시 `SocketTimeoutException` → HikariCP 가 커넥션 폐기. 재배포 후 E 를 다시 실행. 교훈: **풀 타임아웃 ≠ 소켓 타임아웃.** 모든 아웃바운드 소켓(JDBC·Redis·HTTP)에 읽기 상한이 있는지 확인해야 하고, 이건 실험 D·B 처럼 정상 경로에서는 절대 드러나지 않는다 |

## 8. Redis 지연 시나리오가 재현되지 않음 — `DEBUG SLEEP` 비활성

| 단계 | 내용 |
|---|---|
| **증상** | `redis-cli DEBUG SLEEP 3` 가 0.2 s 만에 반환, 프로브 110건 전부 REDIS 경로, fallback 0건 |
| **가설** | 1) Lettuce 타임아웃(200 ms)이 안 걸릴 만큼 짧았다 2) DEBUG 명령이 실행되지 않았다 |
| **검증** | `docker exec spike-redis redis-cli DEBUG SLEEP 0` → `ERR DEBUG command not allowed. If the enable-debug-command option is set to "local"…` (Redis 7 기본값 `enable-debug-command no`) |
| **결론** | 원인 = 2). 스크립트가 종료 코드를 무시해 조용히 통과 |
| **해결** | `docker pause spike-redis; sleep 3; docker unpause` 로 무응답을 재현. 재실행 결과 fallback 전환 192 ms, 복귀 2.3 s, 오류 0. 부수 교훈: 장애 주입 명령은 반드시 "주입이 실제로 됐는지"를 별도로 확인한다(여기서는 `redis_counter_failure_total` 증가 여부) |

## 9. 프로브 지연 절대값이 80 ms — 측정 도구 오버헤드

| 단계 | 내용 |
|---|---|
| **증상** | 장애 프로브(`probe.sh`)의 p50 이 78–87 ms. k6 로 잰 같은 API 는 p50 3–4 ms |
| **가설** | 1) 장애 실험 중 앱이 느리다 2) 프로브가 요청마다 python·curl 프로세스를 띄우는 오버헤드 |
| **검증** | 장애 주입 전 구간(첫 8–10 s)도 동일하게 ≈ 80 ms. `python3 -c` 기동 ≈ 30 ms × 2 + curl ≈ 10 ms |
| **결론** | 원인 = 2). 절대값은 도구 오버헤드, 변화량(5,011 ms 대기, 5,102 ms 타임아웃)만 의미 있음 |
| **해결** | 기록표에 "지연 열은 변화량만" 명시. 절대 지연은 k6 실험(B/C)을 인용 |
