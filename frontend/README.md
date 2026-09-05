# 스파이크 콘솔 (frontend)

에러 급증 감지기를 지켜보는 온콜 엔지니어용 운영 화면. 핵심 질문 두 개에 답한다: "지금 무엇이 타고 있나", "감지기 자체는 멀쩡한가".

## 실행

```bash
npm install
npm run dev        # http://localhost:5173, /api·/actuator 는 http://localhost:8090 으로 프록시
npm run build      # tsc -b && vite build → dist/
npm run lint
```

백엔드(`backend/`, 8090)가 떠 있어야 데이터가 보인다. 관리자 토큰은 상단 입력창에서 바꾸며 `localStorage`(`spike.adminToken`)에 남는다. 기본값 `admin-token`. 선택한 프로젝트는 `spike.projectId`에 남는다.

## 화면

| 경로 | 내용 |
|---|---|
| `/` | 최근 1분·5분·1시간·24시간 건수, 시간대별 추이(1m/5m/1h 간격), 많이 발생한 에러, 감지기 상태(감지 경로·Redis 회로·fallback 여유·발송 큐), 활성 cooldown 카운트다운 |
| `/errors`, `/errors/:id` | 환경·fingerprint·요청 ID 필터, 페이지네이션, 상세(stack trace, metadata) |
| `/policies` | 정책 목록, 켜기/끄기, 생성·수정·삭제 |
| `/alerts` | 상태 필터, 감지 경로, 시도 횟수, 실패 원인, 다시 보내기 |
| `/fire` | 테스트 이벤트 연속 전송기(환경·종류·fingerprint·건수·간격), 판정 결과 로그, Redis 회로 강제 열기/닫기 |

실시간 화면은 5초 간격으로 다시 불러온다.

## 디자인 메모

- 계측 용지 느낌: 옅은 격자 위에 흰 패널, 그림자 없음. 색은 상단 상태 띠(정상=남색, fallback=노랑, 제한=빨강)와 차트 한 곳에만 크게 쓴다.
- 글꼴: IBM Plex Sans KR 한 벌, 큰 숫자만 IBM Plex Sans Condensed. 숫자는 tabular-nums.
- 상태는 색 + 도형 + 글자를 항상 함께 표시한다(색맹·흑백 인쇄 대비).
- 차트 팔레트(`#2456e6`, `#eb6834`)는 dataviz 검증기 6개 검사를 통과한 값.
