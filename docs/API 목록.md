# 티켓팅 서비스 API 목록

> API 목록 단계의 문서입니다. API의 기능과 경로, 주요 요청·응답의 개요를 정리합니다. 요청·응답의 구체적인 필드명·타입·필수 여부, 검증 규칙 및 오류 응답은 상세 API 명세에서 확정합니다.

## 1. 공통 규칙

- Base path: `/api/v1`
- 요청·응답은 JSON 형식을 사용합니다.

## 2. API 목록

| 개발 우선순위 | 기능 | Method | 경로 | 주요 요청값 | 응답 형태(요약) | 인증/권한 |
|---|---|---|---|---|---|---|
| 1 | 이메일 중복 확인 | GET | `/members/email-availability` | Query: 이메일 | 사용 가능 여부 (이메일 형식이 유효하지 않으면 잘못된 요청 오류) | 불필요 |
| 1 | 회원가입 | POST | `/members` | Body: 이메일, 비밀번호 | 생성된 회원 정보 | 불필요 |
| 1 | 로그인 | POST | `/auth/login` | Body: 이메일, 비밀번호 | 인증 토큰 및 만료 정보 | 불필요 |
| 2 | 공연 등록 | POST | `/performances` | Body: 공연명, 공연 일시, 판매 기간, 판매 수량, 가격 등 | 생성된 공연 정보 | 관리자 |
| 2 | 공연 목록 조회 | GET | `/performances` | Query: `available` (선택) | 공연 목록(공연명, 공연 일시, 판매 기간, 가격, 잔여 수량 등) | 불필요 |
| 2 | 공연 상세 조회 | GET | `/performances/{performanceId}` | Path: 공연 식별자 | 공연 상세 정보 | 불필요 |
| 2 | 공연 정보 수정 | PUT | `/performances/{performanceId}` | Path: 공연 식별자; Body: 수정할 공연의 전체 정보 | 수정된 공연 정보 | 관리자 |
| 3 | 예약 신청 | POST | `/reservations` | Body: 공연 식별자, 예약 수량 | 예약 정보(예약 번호, 상태, 수량, 결제 기한, 결제 금액 등) | 회원 |
| 3 | 내 예약 목록 조회 | GET | `/reservations` | 별도 파라미터 없음(초안) | 본인의 예약 목록(공연 요약, 상태, 수량, 금액, 예약 시각 등) | 회원 |
| 3 | 예약 상세 조회 | GET | `/reservations/{reservationId}` | Path: 예약 식별자 | 예약 상세 정보 및 상태 | 회원(본인 예약) |
| 4 | 결제 | POST | `/reservations/{reservationId}/payment` | Path: 예약 식별자 | 결제 처리 결과 및 예약 상태 | 회원(본인 예약) |
| 4 | 예약 취소 | POST | `/reservations/{reservationId}/cancel` | Path: 예약 식별자 | 취소 처리 결과 및 예약 상태 | 회원(본인 예약) |

### 2.1. 공연 목록 조회 조건

`GET /api/v1/performances`

- `available` 생략 또는 `false`: 전체 공연 목록을 조회합니다.
- `available=true`: 현재 판매 기간 안에 있고 잔여 수량이 있는 공연만 조회합니다.

## 3. 상세 API 명세에서 정의할 내용

각 API에 필요한 범위만 다음 항목을 정의합니다.

- 요청값과 응답값: 필드명, 타입, 필수 여부, 예시
- 입력값 검증 및 업무 규칙
- 성공·실패 시 HTTP 상태 코드와 오류 응답
- 인증 및 권한 조건
- 목록 API의 조회 범위와 기본 정렬 규칙
