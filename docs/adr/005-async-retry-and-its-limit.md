# ADR-005. 알림 발송: @Async + @Retryable, 프로세스 종료 시 유실을 한계로 인정

상태: 채택 (확장 조건 명시)

## 맥락
알림 발송은 외부 I/O라 수집 API 응답에서 분리해야 한다. 채널의 일시 장애는 재시도로, 영구 장애는 이력으로 남겨야 한다.

## 결정
- `AlertDispatcher.dispatch` = `@Async("alertExecutor")`, `AlertSender.send` = `@Retryable(3회, 500 ms × 2)`, `@Recover`로 FAILED. 세 클래스로 분리(자기 호출 프록시 우회 방지).
- Executor: core 4 / max 8 / queue 200 / `AbortPolicy` → 거부 시 FAILED(EXECUTOR_SATURATED). `CallerRuns`는 수집 스레드가 webhook을 기다리게 하므로 배제.
- 재시도: 5xx·429·408·타임아웃·IOException만. 4xx는 즉시 실패. 429 `Retry-After` ≤ 5 s 준수.
- 멱등: `Idempotency-Key: alert-{id}`. 타임아웃 후 재시도로 두 번 도착해도 수신 측이 무시.

## 인정하는 한계
Executor 큐는 JVM 힙이다. 프로세스가 죽으면 큐의 알림은 사라지고 `alert_histories`에 PENDING만 남는다. 장애 실험 "앱 재시작"으로 이를 재현·기록한다.

## 확장 조건
| 관찰 | 다음 단계 |
|---|---|
| kill 후 PENDING 잔류가 실제로 발생 | Transactional Outbox: `alert_histories.status=PENDING` 자체가 outbox. `@Scheduled` 폴러가 `PENDING AND created_at < now−10 s`를 `FOR UPDATE SKIP LOCKED`로 집어 재디스패치 |
| 큐 200 포화가 반복 | 폴러 + 큐 크기 조정, 또는 알림 전용 워커 분리 |
| 수집 INSERT 자체가 병목(실험 C) | Kafka로 수집·저장 분리. 새 장애점: 브로커, 소비자 지연 = 감지 지연 |

## 측정
실험 E: 재시도 전/후 성공률, 중복 수, 발송 p95, `async_executor_queue_size`, probe API p95 변화.
