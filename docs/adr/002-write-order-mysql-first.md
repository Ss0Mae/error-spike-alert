# ADR-002. 쓰기 순서: MySQL INSERT 후 Redis 집계

상태: 채택

## 맥락
이벤트 하나가 두 저장소에 쓰인다. 트랜잭션으로 묶을 수 없으므로 한쪽만 성공하는 상황이 반드시 생긴다.

## 대안
1. Redis 먼저 → 감지가 가장 빠르다. MySQL 실패 시 Redis에만 있는 "유령 카운트"가 생기고, fallback COUNT와 결과가 어긋난다. 클라이언트가 재시도하면 이중 카운트.
2. MySQL 먼저 (채택) → Redis 실패 시 이벤트는 보존되고 그 요청은 DB fallback으로 감지. Redis 카운터는 과소 집계(과대 없음).
3. 병렬 → 둘 다 실패 조합이 늘고 이득은 수 ms.

## 결정
2. INSERT는 별도 트랜잭션으로 커밋한 뒤 평가한다(커밋 전에 fallback COUNT가 자기 행을 못 보는 문제와, 비동기 dispatcher가 미커밋 PENDING 행을 읽는 문제를 피하기 위해).

## 결과
- 정합성: 저장은 at-least-once + `eventId` 멱등 = effectively-once. 감지는 과소 방향 오차만 허용.
- 단점: 수집 API 지연 = INSERT + Redis 왕복. INSERT가 병목이면 §19 비동기 저장으로.

## 측정
실험 C의 `error_ingestion_duration_seconds` 대비 `error_counter_duration_seconds` 비율.
