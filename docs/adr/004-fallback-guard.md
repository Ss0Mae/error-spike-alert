# ADR-004. DB fallback 보호: breaker·세마포어·1초 캐시·쿼리 타임아웃·degraded

상태: 채택

## 맥락
Redis 장애 시 모든 이벤트가 `COUNT` 범위 쿼리가 된다. 에러 폭주(=fallback이 필요한 순간)에 커넥션 풀(10)이 COUNT에 잠식되면 INSERT까지 밀려 수집 전체가 느려진다. "무조건 fallback"은 Redis 장애를 DB 장애로 전파한다.

## 대안과 채택
| 장치 | 채택 | 이유 |
|---|---|---|
| Redis circuit breaker(5 s OPEN, 1회 probe) | ○ | 실패마다 200 ms 타임아웃을 기다리지 않게 |
| 동시 fallback 세마포어(8) | ○ | 풀 10 중 최소 2개는 INSERT 몫 |
| 1초 로컬 캐시 + 로컬 증가 | ○ | 같은 fingerprint 폭주를 1 QPS로 축소, 단일 인스턴스에선 정확 |
| `MAX_EXECUTION_TIME(1000)` | ○ | 느린 COUNT가 스레드를 잡지 않게 |
| 샘플링 | ✗ | 임계값 감지 정확도 손실. 캐시가 같은 효과 |
| 별도 스레드 풀 | ✗ | 세마포어로 충분. 풀 추가 = 큐·거부 정책 추가 |
| Resilience4j | ✗ | 40줄로 되는 것에 라이브러리·설정 추가 |

## 결정
세마포어 실패·타임아웃 → 그 이벤트의 감지를 생략(SKIPPED), `detection_skipped_total++`, 상태 DEGRADED. 수집(INSERT)은 끝까지 지킨다.

## 결과
- 트레이드오프: degraded에서 감지 누락을 정직하게 기록하는 대신 DB를 보호.
- 단점: 다중 인스턴스에서 캐시 구간(≤1 s) 동안 다른 인스턴스 이벤트 미반영(과소).

## 측정
장애 실험 "Redis 중단": permits 8 vs 무제한일 때 HikariCP pending, INSERT p95, `detection_skipped_total`. `FallbackIT` permits=1 케이스.
