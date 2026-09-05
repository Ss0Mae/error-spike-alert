# ADR 목록

| 번호 | 제목 | 상태 |
|---|---|---|
| [001](001-window-algorithm.md) | Redis 윈도우 알고리즘: 1초 버킷 근사 슬라이딩 윈도우 | 채택 |
| [002](002-write-order-mysql-first.md) | 쓰기 순서: MySQL INSERT 후 Redis 집계 | 채택 |
| [003](003-cooldown-atomic-and-db-guard.md) | cooldown: SET NX EX + alert_histories.dedup_key UNIQUE | 채택 |
| [004](004-fallback-guard.md) | DB fallback 보호: breaker·세마포어·1초 캐시·쿼리 타임아웃·degraded | 채택 |
| [005](005-async-retry-and-its-limit.md) | 알림 발송: @Async + @Retryable, 프로세스 종료 시 유실을 한계로 인정 | 채택 |
| [006](006-received-at-as-detection-clock.md) | 감지 기준 시각: received_at(서버 시계) | 채택 |
| [007](007-idempotency-event-id.md) | 수집 멱등성: requestId가 아닌 eventId | 채택 |

형식: 맥락 → 대안 → 결정 → 결과(장점·단점) → 측정 방법. 실측값은 `../experiments/results.md`에만 쓴다.
