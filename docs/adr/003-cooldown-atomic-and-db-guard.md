# ADR-003. cooldown: SET NX EX + alert_histories.dedup_key UNIQUE

상태: 채택

## 맥락
여러 인스턴스·스레드가 동시에 임계값을 넘는다. 같은 알림은 한 번만 나가야 하고, Redis가 죽어도 그래야 한다.

## 대안
| 방법 | 문제 |
|---|---|
| `GET` 후 `SET` | 두 명령 사이 경쟁 |
| `SET NX EX` (Redis 정상) | 충분. 단일 스레드 직렬화 |
| Redis 장애 시 `SELECT` 후 `INSERT` | 경쟁 재현 |
| 별도 `alert_occurrences` 테이블 UNIQUE | 테이블 하나 추가. 같은 정보를 이력 테이블 컬럼으로 표현 가능 |
| DB 행 잠금(`SELECT ... FOR UPDATE`) | 정책·fingerprint별 잠금 행 관리 필요 |

## 결정
- 1차: `SET cd:{policyId}:{fpKey} NX EX cooldown`.
- 2차(최종 방어선): `alert_histories.dedup_key = {policyId}:{fpKey}:{floor(epochSec / cooldown)}` UNIQUE. Redis 정상일 땐 TTL 길이 = 슬롯 길이라 충돌하지 않고, Redis 유실·장애·시계 편차 때만 작동.
- 반복형 알림(cooldown 만료마다 재알림). "처음 넘을 때만"은 상태 유실 시 영영 침묵하는 위험이 있어 배제.
- 발송 실패 시 cooldown 키 **유지**. 삭제하면 채널 장애 중 감지→발송→실패가 이벤트마다 반복된다.

## 결과
- 단점: fallback 중 cooldown은 고정 슬롯 근사(슬롯 경계에서 1초 간격 2회 가능). 실패 알림은 cooldown 종료까지 재감지되지 않으므로 대시보드에서 FAILED를 강조하고 수동 retry 제공.

## 측정
실험 D: cooldown 0 vs 30 s 알림 수, `cooldown_contention_total`, webhook `duplicates`. `ConcurrencyIT` 100 스레드.
