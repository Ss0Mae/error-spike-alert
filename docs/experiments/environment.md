# 실험 환경 (모든 실측값의 전제)

| 항목 | 값 |
|---|---|
| 머신 | Apple M5, 10코어, 32 GB RAM, macOS 26 |
| 앱 실행 | 호스트 JVM (Docker 아님). `./gradlew bootRun`, JVM `-Xms1g -Xmx1g -XX:+UseG1GC`, Java 21.0.2 |
| MySQL | 8.0 (Docker), `innodb_buffer_pool_size=1G`, `max_connections=200`, `slow_query_log=1 long_query_time=0.05 log_output=TABLE`, HikariCP pool 10 |
| Redis | 7 (Docker), 영속화 없음(`--save "" --appendonly no`), Lettuce command timeout 200 ms |
| 컨테이너 런타임 | Docker Desktop 29.6 (Apple VM). MySQL·Redis 는 VM 안에서 돌아 호스트 JVM 대비 가상화·포트포워딩 오버헤드가 있다 → **절대 수치는 보수적(느린 쪽)** 이며 경로 간 상대 비교가 목적 |
| 부하 도구 | k6 v2.1 (호스트). 같은 머신에서 실행하므로 k6 CPU 도 앱과 경쟁한다 |
| 데이터셋 | `load/seed.js` → `error_events` 100만 건 (프로젝트 1, 환경 4종, fingerprint 200종, 최근 24 h 균등, 10건마다 1 KB stack trace) |
| 정책 | 프로젝트 1 정책 1을 실험마다 PATCH (W/T/cooldown 은 실험표 참조) |
| Webhook | `mock-webhook/server.js` (Docker node:22-alpine), 앱→`http://localhost:8091/webhook` |
| 반복 | 실험당 5회, 중앙값 ± 표준편차 (`load/summarize.js`). 편차가 중앙값의 20 %를 넘으면 원인(GC·다른 프로세스)을 기록하고 재실행 |
| 시간 | 실측 일자·시각을 `results.md` 각 표 위에 기록 |

측정 채널
- k6 요약(`http_req_duration` p50/p95/p99, `http_reqs` rate, `http_req_failed`)
- 앱 Prometheus (`/actuator/prometheus`): `error_counter_duration_seconds{path}`, `database_fallback_*`, `alerts_*`, `hikaricp_*`, `async_executor_*`
- webhook `/stats`: `detectionDelayMs`(webhook 수신 − trigger 이벤트 receivedAt), `dispatchDelayMs`, `unique`, `duplicates`
- `docker stats` 5초 샘플 → CPU % (MySQL·Redis)
- `EXPLAIN ANALYZE` + `Handler_read_*` (`load/sql/explain-fallback.sql`)
