# 면접 예상 질문과 답변

실측값이 들어가는 답은 `experiments/results.md`·`experiments/failure-scenarios.md`의 수치를 그대로 인용했다.

## 설계 전반

**1. 왜 MySQL과 Redis를 둘 다 쓰나? 하나로 안 되나?**
MySQL만 쓰면 이벤트마다 시간 범위 COUNT가 돌아 DB 부하와 감지 지연이 커진다(실측: 시간축 인덱스 없는 DB 경로에서 100 RPS 기준 API p95 1,009 ms, MySQL 7.7코어, 감지 98 % 생략). Redis만 쓰면 이력·감사 데이터가 없고 Redis 장애 = 감지 중단이다. 그래서 MySQL을 원본(source of truth), Redis를 파생 카운터(cache)로 두고, Redis가 죽으면 원본에서 다시 세는 구조다. 역할이 겹치지 않는다는 점이 중요하다. Redis에는 "다시 만들 수 있는 것"만 넣는다.

**2. 쓰기 순서를 MySQL 먼저로 한 이유는?**
원본 없는 카운트를 만들지 않기 위해서다. Redis 먼저 쓰고 MySQL이 실패하면 Redis에만 있는 유령 카운트가 생기고, 클라이언트 재시도 시 이중 카운트된다. MySQL 먼저면 실패 조합은 "MySQL 성공·Redis 실패" 하나뿐이고, 이 경우 그 요청은 DB fallback으로 감지되며 Redis 카운터는 과소 방향으로만 틀린다. 과대 오차(오탐)는 만들지 않는다는 원칙이다. (ADR-002)

**3. 정합성 수준을 한 문장으로?**
저장은 at-least-once + eventId 멱등 = effectively-once. 감지는 best-effort이되 과소 방향 오차만 허용. 알림은 at-least-once 발송 + 수신 측 멱등 키, 단 프로세스 종료 시 큐 유실 가능(한계로 명시, Outbox가 다음 단계).

## 윈도우 알고리즘

**4. INCR + EXPIRE는 왜 슬라이딩 윈도우가 아닌가?**
키가 분 단위로 고정되어 59초에 19건, 다음 분 1초에 19건이면 60초 안에 38건인데도 각 창에서 19건이라 감지되지 않는다. 반대로 창 경계 직후 리셋되어 정확히 T건이 나와야 다시 잡힌다. 경계 오차가 최대 2배다.

**5. ZSET 대신 1초 버킷을 고른 이유는?**
ZSET은 이벤트마다 멤버를 저장하므로 급증 순간에 키가 가장 커진다(1,000 RPS × 60초 = 6만 멤버, ~2.4 MB). ZREMRANGEBYSCORE와 ZADD가 한 키에 몰리는 hot key 문제도 커진다. 1초 버킷 HASH는 필드가 최대 W개(60)라 메모리가 트래픽과 무관하고, 오차는 1초(1.7 %)다. 임계값이 "20건" 같은 도메인에서 1초 오차는 의미가 없다. 대신 "정확한 슬라이딩"이라고 부르지 않고 "1초 버킷 슬라이딩 윈도우(근사)"라고 명시한다. (ADR-001)

**6. Lua를 쓴 이유는? MULTI/EXEC로는 안 되나?**
HINCRBY → HGETALL → 조건부 HDEL → 합산은 읽은 값에 따라 다음 명령이 달라지므로 MULTI/EXEC(명령 큐잉)로는 표현이 안 된다. Lua는 서버에서 원자적으로 실행되고 왕복이 1회다. 스크립트가 짧아(60필드 순회) Redis 단일 스레드를 오래 잡지 않는다.

**7. Redis hot key는 어떻게 되나?**
같은 fingerprint 폭주면 키 하나에 HINCRBY가 몰린다. 필드 수가 60으로 고정이라 연산 자체는 가볍고, 실측 1,000 RPS(fingerprint 50개)에서 Redis 집계 p95 1.3 ms였고, 단일 fingerprint 1,000 RPS × 10초 대량 유입에서 Redis CPU 최대 5.5 %, 알림 1건·SUPPRESSED 9,981건, API p95 4.3 ms였다. 한계를 넘으면 키를 `:{shard}`로 나눠 합산하거나 Redis Cluster로 간다(§19).

## cooldown·동시성

**8. 100개 요청이 동시에 임계값을 넘으면 어떻게 하나만 알림을 보내나?**
`SET cd:{policy}:{fp} NX EX cooldown`. Redis는 단일 스레드로 명령을 직렬화하므로 정확히 하나만 OK를 받는다. GET 후 SET은 두 명령 사이에 다른 인스턴스가 끼어들 수 있어 안 된다. 테스트로 100 스레드 동시 초과 → AlertHistory 1건, webhook 수신 1건을 검증했다.

**9. Redis가 죽으면 cooldown은?**
DB의 `alert_histories.dedup_key = {policy}:{fp}:{floor(epochSec/cooldown)}` UNIQUE 제약이 최종 방어선이다. 동시 INSERT 중 하나만 성공한다. 이건 Redis 정상일 때도 항상 켜져 있어서 Redis 데이터 유실(재시작·flush)이나 시계 편차에도 작동한다. 대신 fallback 중 cooldown은 TTL이 아니라 고정 슬롯 근사가 되어 슬롯 경계에서 1초 간격 2회 알림이 가능하다는 한계를 문서화했다. (ADR-003)

**10. 알림이 실패하면 cooldown 키를 지워야 하지 않나?**
지우면 채널 장애 중에 이벤트마다 감지→발송→실패가 반복되어 executor와 채널을 더 압박한다. 키를 유지하고 FAILED 이력 + 재시도(자동 3회, 수동 API)로 전달을 책임진다. 대신 cooldown 동안 새 알림이 없으므로 FAILED를 대시보드에서 강조한다.

**11. "처음 넘을 때만" 알림과 "cooldown마다" 알림 중 왜 후자인가?**
전자는 넘음/내려옴 상태를 저장해야 하고, 상태가 유실되면(Redis 장애) 영영 침묵한다. 후자는 무상태고 지속 장애를 주기적으로 상기시킨다. 시끄러우면 cooldown 값을 늘리면 된다.

## fallback

**12. fallback이 DB를 죽일 수 있는데 왜 하나?**
안 하면 감지가 0이 된다. 대신 "DB가 감당할 만큼만" 감지한다: Redis breaker(5초 OPEN), 동시 COUNT 세마포어 8(풀 10 중 2개는 INSERT 몫), 같은 키 1초 로컬 캐시(같은 fingerprint 폭주를 1 QPS로), `MAX_EXECUTION_TIME(1000)`. 넘치면 그 이벤트는 SKIPPED로 정직하게 기록하고 상태를 DEGRADED로 노출한다. 수집(INSERT)은 끝까지 지킨다. (ADR-004)

**13. fallback COUNT 쿼리의 인덱스는?**
`(project_id, environment, fingerprint, received_at)`. 등치 3개 뒤에 범위 1개라 인덱스만으로 COUNT가 끝난다(covering). examined rows가 윈도우 내 건수와 같다. 인덱스를 지우면 100만 건 전체를 읽어 EXPLAIN ANALYZE 기준 696 ms(인덱스 有 1.1 ms), 부하에서는 매 쿼리가 1초 타임아웃에 걸렸다. 반대로 인덱스가 있으면 fallback 경로 p95가 8.0 ms로 Redis 경로(9.3 ms)와 같았다 — 1초 로컬 캐시 덕분이고, 차이는 MySQL CPU(13.5 % vs 10.0 %)와 다중 인스턴스 정확도다. 인덱스 순서를 `(fingerprint, ...)`로 하지 않은 이유는 프로젝트별 조회·환경별 조회가 같은 인덱스의 접두사를 공유하게 하기 위해서다.

**14. occurred_at 대신 received_at을 쓴 이유는?**
클라이언트 시계는 믿을 수 없고, 지연 도착 이벤트를 과거 창에 소급해 봐야 이미 지나간 창은 감지되지 않는다. Redis 버킷도 서버 시각이므로 두 경로가 같은 정의를 갖는다. fallback 쿼리의 기준 시각도 DB NOW()가 아니라 앱이 넘긴다. (ADR-006)

**15. Redis 복구는 어떻게 아나?**
breaker가 5초 OPEN 후 HALF_OPEN으로 한 번 probe한다. 성공하면 CLOSED. 복구 직후 W초 동안은 장애 구간 이벤트가 Redis에 없어 과소 집계된다 — 과대가 아니므로 허용.

## 비동기·재시도

**16. @Async와 @Retryable을 같은 클래스에 두면?**
둘 다 프록시 기반이라 `this.method()` 자기 호출은 프록시를 거치지 않는다. 그래서 Evaluator → Dispatcher(@Async) → Sender(@Retryable) 세 클래스다. 순서는 Async 안에서 Retryable을 부른다. 반대면 재시도가 Future만 받고 끝난다. void @Async의 예외는 호출자에게 안 오므로 dispatcher가 전부 잡아 FAILED로 기록한다.

**17. 재시도가 중복 알림을 만들 수 있지 않나?**
서버가 처리했는데 클라이언트가 타임아웃으로 실패 판정하면 그렇다. `Idempotency-Key: alert-{id}`를 보내고 수신 측이 같은 키를 무시한다. 실험 E의 500·429·timeout 세 조건 모두에서 webhook이 센 duplicates는 0건이었다. timeout 모드는 '서버는 받았는데 클라이언트는 실패로 판정'하는 바로 그 경우인데도 0이었다.

**18. Executor가 꽉 차면?**
`AbortPolicy`로 거부하고 FAILED(EXECUTOR_SATURATED)로 기록한다. CallerRunsPolicy는 수집 API 스레드가 webhook을 기다리게 만들어 장애를 전파한다. 큐 200이 차는 것 자체가 채널 장애 신호다.

**19. @Async의 근본 한계와 다음 단계는?**
큐가 JVM 힙이라 프로세스가 죽으면 사라진다. 실험으로 kill -9 후 큐에 있던 PENDING 135건이 재시작 후에도 그대로 남는 것을 확인했다(100 % 유실, retry API는 FAILED만 받아 복구 경로도 없음). 가장 작은 다음 단계는 `alert_histories` 자체를 outbox로 쓰는 폴러(PENDING AND created_at < now−10s, SKIP LOCKED)다. Kafka는 알림이 아니라 수집 INSERT가 병목일 때(실험 C 1,000 RPS 결과) 수집·저장 분리용으로 검토한다. 기술을 먼저 붙이지 않고 측정 결과가 요구할 때 붙인다. (ADR-005)

**20. 이 시스템에서 가장 먼저 무너지는 지점은?**
실측상 200 RPS부터 수집 API 서버 측 p95가 6 ms → 101 ms로 뛰었고, 원인은 HikariCP 커넥션 10개 포화(pending 39, 500 RPS에서 189)였다. Redis 집계는 1,000 RPS에서도 p95 1.3 ms로 변화가 없었고, MySQL 문장 자체도 0.37 ms인데 커밋(fsync)이 2.49 ms라 쓰기 경로의 상한을 커밋이 정한다. 그 다음이 fallback 중 DB COUNT다. 둘 다 메트릭(`hikaricp_connections_pending`, `detection_skipped_total`)으로 보이게 했다.

## 측정하면서 배운 것

**21. 실험 중 예상 못 한 장애가 있었나?**
있었다. 알림 서버 장애 실험이 80초가 아니라 90분 걸렸다. `jstack`을 떠 보니 Tomcat 요청 스레드가 MySQL 드라이버의 `readFully`에서 37분째 멈춰 있었다. JDBC URL에 `socketTimeout`이 없어(기본 무한) Docker 포트 포워딩이 조용히 끊은 연결의 응답을 TCP 재전송 포기(약 17분)까지 기다린 것이다. HikariCP `connection-timeout`은 커넥션을 "얻는" 시간만 제한하고 이미 얻은 커넥션의 읽기는 보호하지 않는다. `socketTimeout=5000`을 넣어 해결했다. 교훈은 "풀 타임아웃과 소켓 타임아웃은 다르다"와 "정상 경로 실험만으로는 절대 안 보인다"는 것. (`docs/troubleshooting.md` #7)

**22. Redis가 DB보다 빠를 거라 예상했는데 아니었다면?**
실제로 인덱스 있는 DB fallback의 p95(8.0 ms)가 Redis 경로(9.3 ms)보다 낮게 나왔다. 처음엔 측정 오류를 의심했지만 `database_fallback_total`이 요청 수와 일치해 경로는 확실했고, 원인은 fallback의 1초 로컬 캐시가 절반을 흡수한 것이었다. 수치를 그대로 기록하고 주장을 "Redis가 더 빠르다"에서 "Redis는 DB CPU를 쓰지 않고 인스턴스 간 정확한 집계를 준다"로 고쳤다. 유리한 수치만 고르지 않는 것이 실측의 가치다. (`docs/troubleshooting.md` #5)
