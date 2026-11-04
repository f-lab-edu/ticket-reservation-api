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

### 1.3. 인증

인증이 필요한 API는 로그인으로 받은 액세스 토큰을 `Authorization` 헤더에 담아 호출합니다.

```
Authorization: Bearer {accessToken}
```

- 인증에 실패하면 [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750)에 따라 `WWW-Authenticate` 헤더를 함께 반환합니다. 헤더에는 실패 원인(`error_description`)과 참고 링크(`error_uri`)가 포함될 수 있으며, 본문의 `detail`은 실패 원인과 관계없이 고정 문구입니다.

| 상황 | status | code | `WWW-Authenticate` |
|---|---|---|---|
| Bearer 토큰을 보내지 않음 (`Authorization` 헤더가 없거나 Basic 등 다른 인증 방식) | 401 | `UNAUTHORIZED` | `Bearer` |
| 액세스 토큰이 만료됐거나 서명·형식이 올바르지 않음 | 401 | `UNAUTHORIZED` | `Bearer error="invalid_token", error_description="...", error_uri="..."` |

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

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 1800
}
```

#### 오류

| code | status | 상황                                                     |
|---|---|--------------------------------------------------------|
| - | 400 | `email`이 없거나 비어 있음<br>`password`가 없거나 비어 있거나 72바이트를 초과함 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않음 |
