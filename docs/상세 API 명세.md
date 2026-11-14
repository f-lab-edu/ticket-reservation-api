# 티켓팅 서비스 상세 API 명세

> `API 목록.md`의 API별 요청·응답 형식과 오류 응답을 정의합니다.
> 기능별 동작 규칙은 `기능정의서.md`를 따르며, 해당 항목 번호를 표기합니다.
> API는 구현 시점에 이 문서에 추가합니다.

## 1. 공통 규칙

- Base path: `/api/v1`
- 요청·응답 본문은 JSON을 사용합니다.

### 1.1. 오류 응답

오류 응답은 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) ProblemDetail 형식(`application/problem+json`)을 사용합니다.

| 필드 | 설명 |
|---|---|
| `type` | 항상 `about:blank` |
| `title` | HTTP 상태 이름 (예: `Conflict`) |
| `status` | HTTP 상태 코드 |
| `detail` | 오류 설명 |
| `instance` | 요청 경로 |
| `code` | 오류 코드 (1.2). 1.2의 오류 코드에 해당할 때만 포함됩니다. |

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 사용 중인 이메일입니다.",
  "instance": "/api/v1/members",
  "code": "DUPLICATE_EMAIL"
}
```

### 1.2. 오류 코드

| code | status | detail |
|---|---|---|
| `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일입니다. |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| `UNAUTHORIZED` | 401 | 인증 정보가 없거나 유효하지 않습니다. |
| `INVALID_REFRESH_TOKEN` | 401 | 리프레시 토큰이 유효하지 않습니다. |
| `FORBIDDEN` | 403 | 요청한 작업을 수행할 권한이 없습니다. |
| `AUTH_STORE_UNAVAILABLE` | 503 | 인증 서비스를 일시적으로 사용할 수 없습니다. |
| `INVALID_SALE_START_AT` | 400 | 판매 시작 일시는 현재 시각보다 늦어야 합니다. |
| `INVALID_SALE_PERIOD` | 400 | 판매 종료 일시는 판매 시작 일시보다 늦어야 합니다. |
| `SALE_END_AFTER_PERFORMANCE` | 400 | 판매 종료 일시는 공연 일시보다 빨라야 합니다. |
| `PERFORMANCE_NOT_FOUND` | 404 | 공연을 찾을 수 없습니다. |

### 1.3. 인증

인증이 필요한 API는 로그인 또는 토큰 재발급(3.2)으로 받은 액세스 토큰을 `Authorization` 헤더에 담아 호출합니다. 액세스 토큰이 만료되면 토큰 재발급으로 새 토큰을 받습니다.

```
Authorization: Bearer {accessToken}
```

- 인증에 실패하면 [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750)에 따라 `WWW-Authenticate` 헤더를 함께 반환합니다. 헤더에는 실패 원인(`error_description`)과 참고 링크(`error_uri`)가 포함될 수 있으며, 본문의 `detail`은 실패 원인과 관계없이 고정 문구입니다.

| 상황 | status | code | `WWW-Authenticate` |
|---|---|---|---|
| Bearer 토큰을 보내지 않음 (`Authorization` 헤더가 없거나 Basic 등 다른 인증 방식) | 401 | `UNAUTHORIZED` | `Bearer` |
| 액세스 토큰이 만료됐거나, 무효화됐거나, 서명·형식이 올바르지 않음 | 401 | `UNAUTHORIZED` | `Bearer error="invalid_token", error_description="...", error_uri="..."` |
| 인증은 됐지만 API에 필요한 권한이 없음 (예: 관리자 API를 일반 회원이 요청) | 403 | `FORBIDDEN` | 없음 |
| 로그인 세션 무효화 여부를 확인할 수 없음 (토큰 저장소 장애) | 503 | `AUTH_STORE_UNAVAILABLE` | 없음 |

- 인증이 필요한 API는 모두 위 표의 오류를 반환할 수 있습니다. API별 오류 표에는 이 오류를 반복해 적지 않고, 해당 API에서만 발생하는 오류만 적습니다.
- 액세스 토큰 만료는 60초의 기본 허용 오차를 두고 판정합니다. 만료 시각(`exp`)이 지난 뒤 60초 이내의 토큰은 아직 유효한 것으로 처리합니다.
- 인증이 필요 없는 API는 `Authorization` 헤더를 확인하지 않습니다. 만료된 토큰을 붙여 호출해도 거절되지 않습니다.

## 2. 회원

### 2.1. 이메일 중복 확인

`GET /api/v1/members/email-availability` · 인증 불필요 · 기능정의서 4.1

#### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `email` | string | O | 확인할 이메일 (형식: 4.1.1) |

#### 응답: `200 OK`

| 필드 | 타입 | 설명 |
|---|---|---|
| `available` | boolean | 가입 가능하면 `true`, 이미 사용 중이면 `false` |

```json
{ "available": true }
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `email`이 없거나 비어 있거나 형식이 올바르지 않음 |

### 2.2. 회원 가입

`POST /api/v1/members` · 인증 불필요 · 기능정의서 4.2

#### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 이메일 (형식: 4.1.1) |
| `password` | string | O | 비밀번호 (UTF-8 기준 72바이트 이하, 4.2.1.1.1) |

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

#### 응답: `201 Created`

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 회원 식별자 |
| `email` | string | 정규화된 이메일 (4.1.2) |
| `role` | string | 회원 권한 (`USER`) |

```json
{
  "id": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `email`이 없거나 비어 있거나 형식이 올바르지 않음<br>`password`가 없거나 비어 있거나 72바이트를 초과함 |
| `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일 |

## 3. 인증

### 3.1. 로그인

`POST /api/v1/auth/login` · 인증 불필요 · 기능정의서 4.3.1

#### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 가입한 이메일 (가입 시와 같은 규칙으로 정규화, 4.1.2) |
| `password` | string | O | 비밀번호 (UTF-8 기준 72바이트 이하) |

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

#### 응답: `200 OK`

| 필드 | 타입 | 설명 |
|---|---|---|
| `tokenType` | string | 항상 `Bearer` |
| `accessToken` | string | 액세스 토큰 (JWT). 회원 식별자(`sub`)와 권한(`role`)을 포함 |
| `expiresIn` | number | 액세스 토큰 유효 시간(초), `1800` |
| `refreshToken` | string | 리프레시 토큰 (불투명 난수 문자열, 유효기간 7일) |

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 1800,
  "refreshToken": "q3Jx0b2m..."
}
```

#### 오류

| code | status | 상황                                                     |
|---|---|--------------------------------------------------------|
| - | 400 | `email`이 없거나 비어 있음<br>`password`가 없거나 비어 있거나 72바이트를 초과함 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않음 |
| `AUTH_STORE_UNAVAILABLE` | 503 | 토큰 저장소 장애 |

### 3.2. 토큰 재발급

`POST /api/v1/auth/refresh` · 인증 불필요 · 기능정의서 4.3.2

#### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `refreshToken` | string | O | 로그인 또는 이전 재발급에서 받은 리프레시 토큰 |

```json
{ "refreshToken": "q3Jx0b2m..." }
```

#### 응답: `200 OK`

로그인 응답(3.1)과 같습니다.

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `refreshToken`이 없거나 비어 있음 |
| `INVALID_REFRESH_TOKEN` | 401 | 리프레시 토큰이 존재하지 않거나, 만료됐거나, 이미 사용됐거나, 재사용 감지로 무효화됨 |
| `AUTH_STORE_UNAVAILABLE` | 503 | 토큰 저장소 장애 |

#### 비고
- 이미 사용된 리프레시 토큰으로 요청하면 탈취로 간주하며 해당 토큰이 속한 로그인 세션의 모든 액세스 토큰과 리프레시 토큰을 무효화합니다.
- 동일한 리프레시 토큰으로 동시에 재발급 요청이 발생하면 해당 로그인 세션이 무효화되어 재로그인이 필요합니다.

### 3.3. 로그아웃

`POST /api/v1/auth/logout` · 인증 필요(회원) · 기능정의서 4.3.3

#### 요청

요청 본문이 없습니다. 액세스 토큰(1.3)으로 로그인 세션을 식별합니다.

#### 응답: `204 No Content`

본문이 없습니다.

#### 오류

| code | status | 상황 |
|---|---|---|
| `AUTH_STORE_UNAVAILABLE` | 503 | 토큰 저장소 장애 |

#### 비고
- 같은 로그인 세션의 액세스 토큰과 리프레시 토큰을 모두 즉시 무효화합니다.
- 액세스 토큰이 만료됐으면 재발급(3.2) 후 로그아웃합니다.

## 4. 공연

- 일시는 한국 시간(Asia/Seoul) 기준이며, 시간대 없이 `yyyy-MM-dd'T'HH:mm:ss` 형식(ISO-8601)으로 주고받습니다. 예: `2026-12-20T19:00:00`

### 4.1. 공연 등록

`POST /api/v1/performances` · 인증 필요(관리자) · 기능정의서 4.4

#### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `title` | string | O | 공연명 (공백만으로 구성할 수 없음, 100자 이하) |
| `venue` | string | O | 공연 장소 (공백만으로 구성할 수 없음, 100자 이하) |
| `performanceAt` | string | O | 공연 일시 |
| `price` | number | O | 티켓 가격 (0 이상, 4.4.1.2.1) |
| `totalQuantity` | number | O | 총 판매 수량 (1 이상, 4.4.1.2.2) |
| `saleStartAt` | string | O | 판매 시작 일시 (4.4.1.2.3) |
| `saleEndAt` | string | O | 판매 종료 일시 (4.4.1.2.4, 4.4.1.2.5) |

```json
{
  "title": "콘서트1",
  "venue": "장소A",
  "performanceAt": "2026-12-20T19:00:00",
  "price": 50000,
  "totalQuantity": 100,
  "saleStartAt": "2026-12-01T10:00:00",
  "saleEndAt": "2026-12-19T18:00:00"
}
```

#### 응답: `201 Created`

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 공연 식별자 |
| `title` | string | 공연명 |
| `venue` | string | 공연 장소 |
| `performanceAt` | string | 공연 일시 |
| `price` | number | 티켓 가격 |
| `totalQuantity` | number | 총 판매 수량 |
| `remainingQuantity` | number | 잔여 판매 수량 (등록 시 총 판매 수량과 같음, 4.4.1.3) |
| `saleStartAt` | string | 판매 시작 일시 |
| `saleEndAt` | string | 판매 종료 일시 |

```json
{
  "id": 1,
  "title": "콘서트1",
  "venue": "장소A",
  "performanceAt": "2026-12-20T19:00:00",
  "price": 50000,
  "totalQuantity": 100,
  "remainingQuantity": 100,
  "saleStartAt": "2026-12-01T10:00:00",
  "saleEndAt": "2026-12-19T18:00:00"
}
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | 요청값이 필수 여부·형식·조건을 만족하지 않음 |
| `INVALID_SALE_START_AT` | 400 | 판매 시작 일시가 현재 시각과 같거나 빠름 |
| `INVALID_SALE_PERIOD` | 400 | 판매 종료 일시가 판매 시작 일시와 같거나 빠름 |
| `SALE_END_AFTER_PERFORMANCE` | 400 | 판매 종료 일시가 공연 일시와 같거나 늦음 |

### 4.2. 공연 상세 조회

`GET /api/v1/performances/{performanceId}` · 인증 불필요 · 기능정의서 4.7

#### 요청

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| `performanceId` | path | number | O | 공연 식별자 |

#### 응답: `200 OK`

공연 등록 응답(4.1)과 같습니다.

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `performanceId`가 숫자가 아님 |
| `PERFORMANCE_NOT_FOUND` | 404 | 공연이 존재하지 않음 |

### 4.3. 공연 목록 조회

`GET /api/v1/performances` · 인증 불필요 · 기능정의서 4.5, 4.6

#### 요청

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| `available` | query | boolean | X | `true`면 예약 가능한 공연만 조회합니다(4.5). 생략하거나 `false`면 전체 공연을 조회합니다(4.6). |

- 예약 가능한 공연: 판매 시작 일시 ≤ 현재 시각 < 판매 종료 일시이고 잔여 판매 수량이 1 이상인 공연
- 정렬: 공연 일시 오름차순, 같으면 공연 식별자 오름차순
- 페이징 없이 조건에 맞는 공연을 모두 반환합니다.

#### 응답: `200 OK`

| 필드 | 타입 | 설명 |
|---|---|---|
| `performances` | array | 공연 목록. 조건에 맞는 공연이 없으면 빈 배열 |
| `performances[].id` | number | 공연 식별자 |
| `performances[].title` | string | 공연명 |
| `performances[].venue` | string | 공연 장소 |
| `performances[].performanceAt` | string | 공연 일시 |
| `performances[].price` | number | 티켓 가격 |
| `performances[].remainingQuantity` | number | 잔여 판매 수량 |

```json
{
  "performances": [
    {
      "id": 1,
      "title": "콘서트1",
      "venue": "장소A",
      "performanceAt": "2026-12-20T19:00:00",
      "price": 50000,
      "remainingQuantity": 100
    }
  ]
}
```

#### 오류

| code | status | 상황 |
|---|---|---|
| - | 400 | `available`을 boolean으로 해석할 수 없음 |
