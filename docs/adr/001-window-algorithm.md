# ADR-001. Redis 윈도우 알고리즘: 1초 버킷 근사 슬라이딩 윈도우

상태: 채택 (2026-09)

## 맥락
정책은 "최근 W초 동안 T건 이상"이다. `INCR`+`EXPIRE` 하나로는 고정 윈도우가 되어 경계에서 감지를 놓친다. 정확한 슬라이딩(ZSET)은 이벤트마다 멤버를 저장하므로 급증 상황(=이 시스템이 가장 바빠야 할 순간)에 키가 가장 커진다.

## 대안
| | A 고정 윈도우 | B ZSET 슬라이딩 | C 1초 버킷 |
|---|---|---|---|
| 정확도 | 경계에서 최대 2배 오차 | ms 정확 | ≤ 1초 오차 (60초 창에서 1.7 %) |
| 연산 | O(1) | O(log N)+제거 | O(W) |
| 메모리/키 | 8 B | 이벤트 수 × ~40 B (1k RPS × 60 s ≈ 2.4 MB) | ≤ W 필드 ≈ 1.2 KB |
| hot key | 약 | 강 (큰 키에 ZADD/ZREMRANGEBYSCORE 집중) | 약 |
| 구현 | 3줄 | Lua + 멤버 유일성 | Lua 12줄 |

## 결정
C. HASH `ec:{policyId}:{fpKey}`, field=epoch second, Lua 한 번으로 HINCRBY → 윈도우 밖 HDEL → 합산 → EXPIRE(W+5).
문서·코드·UI에서 "1초 버킷 슬라이딩 윈도우(근사)"라고 부른다. "정확한 슬라이딩 윈도우"라고 쓰지 않는다.

## 결과
- 장점: 메모리가 트래픽과 무관, hot key 완화, DB fallback과 같은 윈도우 정의(`received_at >= trunc(now)−(W−1)s`)를 공유.
- 단점: 1초 오차. W가 3600이면 Lua가 3600 필드를 순회(현재 정책 최대 3600 허용, 대부분 60–300).
- B가 필요한 경우: 개별 이벤트 시각이 필요하거나 T가 매우 작고(≤3) 초 단위 오차가 문제일 때.

## 측정
`error_counter_duration_seconds{path="REDIS"}` p99, Redis CPU(`docker stats`), 실험 C 1,000 RPS에서 Lua 시간. 정확도는 `RedisErrorCounterIT`의 경계 테스트.
