# refactor/hardening — API 변경점 · 배포 메모

FE(Donbugi-FE) 수정이 필요한 부분과 배포 시 확인할 점을 정리한 문서.

## 1. 깨지는 변경 (FE 수정 필요)

| 기존 | 변경 | FE 할 일 |
|---|---|---|
| `POST /api/points/earn` | **삭제 (404)** | 호출부 제거. 적립은 서버 정책 보상으로만 발생 |
| `POST /api/points/rewards/daily-quiz` | **410 Gone** | 아래 "오늘의 과제" 흐름으로 교체 |
| `POST /api/points/rewards/news-detail-quiz` | **410 Gone** | `POST /api/quiz/{articleId}/answer` 로 교체 |
| `POST /api/points/rewards/attendance` body `date` | **무시됨** (서버 오늘 날짜) | body 생략 가능 |
| `POST /api/news/interest/read` | `articleId` 추가 권장 | `{ "articleId": 123 }` 를 보내야 읽기 보상 지급 (category 만 보내면 관심도 집계만) |
| `POST /api/points/redeem` 응답 `mailSent` | 항상 `false` (비동기 발송) | `mailNotice` 문구 표시 |
| 주문 응답 `quantity`·`price` (시장가) | **실제 체결 수량/가중평균가** | 호가 잔량 부족 시 요청 수량보다 적을 수 있음 (`requestedQuantity`, `status` 확인) |
| 지정가 매도 접수 후 보유종목 | 수량 유지 + `lockedQuantity` 증가 | 매도 가능 수량은 `sellableQuantity` 사용 |
| 계좌 `cash` | 예수금 총액(묶인 금액 포함) | 주문 가능 금액은 `availableCash` 사용 |

### 오늘의 과제 (서버 채점)
```
GET  /api/quiz/daily                 → { sessionId, items:[{order, question, options, answered, ...}], ... }
POST /api/quiz/daily/answer          body { "order": 1, "selectedIndex": 2 }
                                     → { correct, correctIndex, explanation, pointsAwarded, bonusAwarded, balanceAfter, sessionCompleted }
```
- 같은 날 다시 조회하면 같은 문항. 정답/해설은 **제출 후에만** 내려옴.
- 슬롯당 1회 제출 (재제출 409 `Q002`).
- `/api/quiz/random-session` 은 연습용으로 유지 (correctIndex 포함, 보상 없음).

### 기사 상세 퀴즈
```
POST /api/quiz/{articleId}/answer    body { "questionIndex": 0, "selectedIndex": 1 }
                                     → { correct, correctIndex, explanation, pointsAwarded, pointsAmount, balanceAfter }
```

## 2. 추가된 API

| API | 설명 |
|---|---|
| `PATCH /api/orders/{id}` body `{limitPrice, quantity}` | 정정 주문 (미체결 지정가) |
| `GET /api/orders?page&size` | 전체 주문 내역 |
| `Idempotency-Key` 헤더 | `POST /api/orders/market`, `/limit`, `PATCH /api/orders/{id}`, `POST /api/points/redeem` — 같은 키 재요청 시 기존 결과 반환(`replayed=true`) |
| `GET /api/account/asset-history?days=30` | 일별 총자산·수익률 (15:40 스냅샷) |
| `GET /api/account/ranking?top=20` | 수익률 랭킹 + 내 순위 |
| `GET /api/account/stream` | 체결 알림 SSE (`event: trade`). EventSource 는 헤더를 못 보내므로 `@microsoft/fetch-event-source` 등 fetch 기반 클라이언트 사용 |
| `GET /api/main/economic-weather/history?days=30` | 경제 날씨 일별 추이 |
| `POST /api/orders/preview` 응답 확장 | `tickSize`, `upperLimit`, `lowerLimit`, `session`, `availableCash`, `sellableQuantity`, `estimatedFillQuantity`, `syntheticOrderBook` |

## 3. 새 에러 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| C003 | 409 | 동시성 충돌 (재시도) |
| C004 | 429 | Rate limit (`Retry-After: 60`) |
| C005 | 410 | 폐기된 API |
| C006 / C007 | 404 / 405 | 없는 경로 / 메서드 (기존엔 500) |
| Q002 / Q003 | 409 / 404 | 이미 제출 / 문항 없음 |
| TR004 | 400 | 주문 가능 시간 아님 (영업일 08:30~15:30, 휴장일) |
| TR009 / TR010 | 400 | 호가단위 위반 / 가격제한폭(±30%) 초과 |
| TR011 | 503 | 시세 지연으로 시장가 불가 |
| TR012 | 409 | 체결 가능한 호가 잔량 없음 |
| TR013 | 400 | 정정 불가 주문 |

## 4. 모의투자 규칙 요약

- 세션: 08:30~09:00 장전 동시호가(접수만, 09:00 시가 일괄 체결) / 09:00~15:20 접속매매 / 15:20~15:30 장마감 동시호가(15:30 종가 일괄 체결) / 15:31 미체결 취소
- 시장가: 호가를 소진하며 체결, 잔량은 취소(IOC). 동시호가 중 시장가 매수는 상한가 기준 금액 lock
- 지정가: 호가단위·±30% 검증, 즉시 체결 가능분은 바로 체결. 이후 20초마다 매칭 (호가 + 구간 저가/고가)
- 수수료 0.015% 원 미만 절사, 거래세 2026년 0.20%
- 휴장일: `mock-trading.holidays` (2026·2027, **KRX 공지로 매년 확인 필요**)
- 호가: `stock.naver.com/api/domestic/detail/{code}/hoga` (비공식). 실패 시 현재가 기준 근사 호가 — `trading_orderbook_fetch_total{source="synthetic"}` 메트릭으로 비율 확인

## 5. 배포 체크리스트

1. **환경변수**
   - `APP_AUTH_JWT_SECRET` 필수 (기본값 제거됨 — 없으면 기동 실패)
   - `MANAGEMENT_PORT` (기본 8081) — 보안그룹에서 외부 차단 권장
   - 레포 이력에 남아 있는 기존 JWT 기본값은 노출된 것으로 보고 **새 시크릿으로 교체** (기존 토큰은 무효화됨)
2. **Flyway**: 첫 기동 시 기존 DB 를 baseline(0) 으로 등록 후 V1~V3 적용
   - V2: 구 `pending_order` 의 미체결 주문을 **예약금 환불·보유수량 복원 후 취소**, 기존 계좌 원장 기초잔액 생성
   - 배포 전 `pg_dump` 백업 권장
3. CI: 빌드 전 `./gradlew test` (Testcontainers), 헬스체크는 `:8081/actuator/health`
4. Grafana: `/actuator/prometheus` (8081) 스크랩 — `trading_reconciliation_mismatch_total`, `points_reconciliation_mismatch_total` 알림 권장

## 6. 남은 과제

- `ddl-auto: update` → `validate` 전환: 운영 스키마 `pg_dump --schema-only` 로 V0 baseline 작성 후
- 경제 날씨 점수의 변동성(z-score) 기반 전환 (과거 지수 시계열 수집 필요)
- 시간외 거래·NXT(대체거래소) 세션은 범위 밖
